package com.qrattendance.backend.service;

import com.qrattendance.backend.config.GroupRulesConfig;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelParserService {

    private final UserService userService;
    private final SubjectService subjectService;
    private final GroupRulesConfig groupRulesConfig;

    @Value("${app.student-email-domain}")
    private String studentEmailDomain;

    @Transactional
    public Subject parseAndSave(MultipartFile file, User createdBy) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.endsWith(".csv")) {
            return parseAndSaveCsv(file, createdBy);
        }
        return parseAndSaveXlsx(file, createdBy);
    }

    // XLSX parser
    @Transactional
    public Subject parseAndSaveXlsx(MultipartFile file, User createdBy) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row titleRow = sheet.getRow(1);
            String titleText = getCell(titleRow, 0, formatter);
            String academicYear = extractAcademicYear(titleText);

            Row metaRow = sheet.getRow(4);
            if (metaRow == null) {
                throw new IllegalArgumentException("Neispravan Excel format – nema reda sa metapodacima (red 5).");
            }

            String name         = getCell(metaRow, 7, formatter);
            String code         = getCell(metaRow, 9, formatter);
            String teachingType = getCell(metaRow, 19, formatter);
            String groupName    = getCell(metaRow, 21, formatter);
            int    studyYear    = parseInt(getCell(metaRow, 15, formatter));

            int startRow = findStudentStartRow(sheet, formatter);
            List<User> students = collectStudentsFromSheet(sheet, formatter, startRow);

            return saveSubjectOrSplit(
                    name, code,
                    getCell(metaRow, 11, formatter),
                    getCell(metaRow, 13, formatter),
                    studyYear,
                    parseInt(getCell(metaRow, 17, formatter)),
                    teachingType, groupName, academicYear,
                    students, createdBy
            );
        }
    }

    // CSV parser
    @Transactional
    public Subject parseAndSaveCsv(MultipartFile file, User createdBy) throws IOException {
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(line.split(";"));
            }

            if (rows.size() < 5) throw new IllegalArgumentException("Neispravan CSV format.");

            String titleText    = rows.size() > 1 ? getcsv(rows.get(1), 0) : "";
            String academicYear = extractAcademicYear(titleText);

            String[] metaRow    = rows.get(4);
            String name         = getcsv(metaRow, 7);
            String code         = getcsv(metaRow, 9);
            String teachingType = getcsv(metaRow, 19);
            String groupName    = getcsv(metaRow, 21);
            int    studyYear    = parseInt(getcsv(metaRow, 15));

            int startRow = 7;
            for (int i = 0; i < Math.min(20, rows.size()); i++) {
                String[] row = rows.get(i);
                if (row.length > 1 && (row[1].toLowerCase().contains("презиме")
                        || row[1].toLowerCase().contains("prezime"))) {
                    startRow = i + 1;
                    break;
                }
            }

            List<User> students = collectStudentsFromCsv(rows, startRow);

            return saveSubjectOrSplit(
                    name, code,
                    getcsv(metaRow, 11),
                    getcsv(metaRow, 13),
                    studyYear,
                    parseInt(getcsv(metaRow, 17)),
                    teachingType, groupName, academicYear,
                    students, createdBy
            );
        }
    }

    // logika: jedan predmet ili auto-podjela po grupama
    private Subject saveSubjectOrSplit(
            String name, String code, String studyProgram, String studyType,
            int studyYear, int semester,
            String teachingType, String groupName, String academicYear,
            List<User> students, User createdBy) {

        boolean shouldSplit = studyYear == 1
                && !isPredavanje(teachingType)
                && hasGroupRules(teachingType);

        if (!shouldSplit) {
            return saveSingleSubject(name, code, studyProgram, studyType,
                    studyYear, semester, teachingType, groupName, academicYear,
                    students, createdBy);
        }

        // Auto-podjela po grupama iz konfiguracije
        Map<String, List<User>> grouped = groupStudentsByIndex(students, teachingType);

        Subject first = null;
        for (Map.Entry<String, List<User>> entry : grouped.entrySet()) {
            String grpName         = entry.getKey();
            List<User> grpStudents = entry.getValue();
            if (grpStudents.isEmpty()) continue;

            // IZMJENA: duplikat je samo ako ga je isti zaposleni vec uploadovao
            if (subjectService.existsForOwner(code, teachingType, grpName, academicYear, createdBy)) {
                continue; // preskoči već postojeće grupe
            }

            Subject s = buildSubject(name, code, studyProgram, studyType,
                    studyYear, semester, teachingType, grpName, academicYear, createdBy);
            Subject saved = subjectService.save(s);
            saved.getStudents().addAll(grpStudents);
            saved = subjectService.save(saved);

            if (first == null) first = saved;
        }

        if (first == null) {
            throw new IllegalArgumentException(
                    "Sve grupe za predmet '" + name + "' (" + teachingType
                    + ") već postoje za " + academicYear + ".");
        }
        return first;
    }

    private Subject saveSingleSubject(
            String name, String code, String studyProgram, String studyType,
            int studyYear, int semester,
            String teachingType, String groupName, String academicYear,
            List<User> students, User createdBy) {

        // IZMJENA: provjera po vlasniku
        if (subjectService.existsForOwner(code, teachingType, groupName, academicYear, createdBy)) {
            throw new IllegalArgumentException(
                    String.format("Predmet '%s' (Grupa: %s, Tip: %s) već postoji za akademsku godinu %s.",
                            name, groupName, teachingType, academicYear));
        }

        Subject subject = buildSubject(name, code, studyProgram, studyType,
                studyYear, semester, teachingType, groupName, academicYear, createdBy);
        Subject saved = subjectService.save(subject);
        saved.getStudents().addAll(students);
        return subjectService.save(saved);
    }

    private Subject buildSubject(
            String name, String code, String studyProgram, String studyType,
            int studyYear, int semester,
            String teachingType, String groupName, String academicYear, User createdBy) {
        Subject s = new Subject();
        s.setName(name);
        s.setCode(code);
        s.setStudyProgram(studyProgram);
        s.setStudyType(studyType);
        s.setStudyYear(studyYear);
        s.setSemester(semester);
        s.setTeachingType(teachingType);
        s.setGroupName(groupName);
        s.setAcademicYear(academicYear);
        s.setCreatedBy(createdBy);
        return s;
    }

    private Map<String, List<User>> groupStudentsByIndex(List<User> students, String teachingType) {
        List<GroupRulesConfig.GroupDef> rules = isLaboratorijske(teachingType)
                ? groupRulesConfig.getLaboratorijske()
                : groupRulesConfig.getAuditorne();

        Map<String, List<User>> groups = new LinkedHashMap<>();
        for (User student : students) {
            String grpName = resolveGroupForIndex(student.getIndexNumber(), rules);
            groups.computeIfAbsent(grpName, k -> new ArrayList<>()).add(student);
        }
        return groups;
    }

    public String resolveGroupForIndex(String index, List<GroupRulesConfig.GroupDef> rules) {
        if (index == null || !index.contains("/")) return "Ostali";
        try {
            int num = Integer.parseInt(index.split("/")[0]);
            return groupRulesConfig.resolveGroup(num, rules);
        } catch (Exception e) {
            return "Ostali";
        }
    }

    public List<GroupRulesConfig.GroupDef> getAuditorneRules() {
        return groupRulesConfig.getAuditorne();
    }

    public List<GroupRulesConfig.GroupDef> getLaboratorijskeRules() {
        return groupRulesConfig.getLaboratorijske();
    }

    // Provjera konfiguracije i tipa nastave

    private boolean hasGroupRules(String teachingType) {
        if (isLaboratorijske(teachingType)) {
            return groupRulesConfig.getLaboratorijske() != null
                    && !groupRulesConfig.getLaboratorijske().isEmpty();
        }
        return groupRulesConfig.getAuditorne() != null
                && !groupRulesConfig.getAuditorne().isEmpty();
    }

    private boolean isPredavanje(String teachingType) {
        if (teachingType == null) return false;
        String t = teachingType.toLowerCase().trim();
        return t.contains("predavanj") || t.contains("предавањ");
    }

    public boolean isLaboratorijske(String teachingType) {
        if (teachingType == null) return false;
        String t = teachingType.toLowerCase().trim();
        return t.contains("laborator") || t.contains("лаборатор");
    }

    // Prikupljanje studenata

    private List<User> collectStudentsFromSheet(Sheet sheet, DataFormatter formatter, int startRow) {
        List<User> students = new ArrayList<>();
        int emptyRowCount = 0;

        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                if (++emptyRowCount > 3) break;
                continue;
            }
            String lastName      = getCell(row, 1, formatter);
            String firstName     = getCell(row, 2, formatter);
            String indexNumber   = getCell(row, 3, formatter);
            String studentStatus = getCell(row, 4, formatter);

            if (indexNumber.isBlank()) {
                if (++emptyRowCount > 3) break;
                continue;
            }
            emptyRowCount = 0;
            if (firstName.isBlank() || lastName.isBlank()) continue;

            students.add(getOrCreateStudent(firstName, lastName, indexNumber, studentStatus));
        }
        return students;
    }

    private List<User> collectStudentsFromCsv(List<String[]> rows, int startRow) {
        List<User> students = new ArrayList<>();
        int emptyRowCount = 0;

        for (int i = startRow; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length < 4) {
                if (++emptyRowCount > 3) break;
                continue;
            }
            String lastName      = getcsv(row, 1);
            String firstName     = getcsv(row, 2);
            String indexNumber   = getcsv(row, 3);
            String studentStatus = row.length > 4 ? getcsv(row, 4) : "";

            if (indexNumber.isBlank()) {
                if (++emptyRowCount > 3) break;
                continue;
            }
            emptyRowCount = 0;
            if (firstName.isBlank() || lastName.isBlank()) continue;

            students.add(getOrCreateStudent(firstName, lastName, indexNumber, studentStatus));
        }
        return students;
    }

    // IZMJENA: postojeci korisnik (npr. kreiran prijavom prije uploada) dobija indeks i status iz liste
    private User getOrCreateStudent(String firstName, String lastName,
                                     String indexNumber, String studentStatus) {
        String email = generateEmail(firstName, lastName);
        return userService.findByEmail(email)
                .map(existing -> {
                    boolean changed = false;
                    if (existing.getFirstName() == null || existing.getFirstName().isBlank()) {
                        existing.setFirstName(firstName);
                        existing.setLastName(lastName);
                        changed = true;
                    }
                    if (existing.getIndexNumber() == null || existing.getIndexNumber().isBlank()) {
                        existing.setIndexNumber(indexNumber);
                        changed = true;
                    }
                    if (!studentStatus.isBlank() && !studentStatus.equals(existing.getStudentStatus())) {
                        existing.setStudentStatus(studentStatus);
                        changed = true;
                    }
                    return changed ? userService.save(existing) : existing;
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setFirstName(firstName);
                    newUser.setLastName(lastName);
                    newUser.setRole(User.UserRole.STUDENT);
                    newUser.setIndexNumber(indexNumber);
                    newUser.setStudentStatus(studentStatus);
                    return userService.save(newUser);
                });
    }

    // Pomoćne metode
    private String getCell(Row row, int col, DataFormatter formatter) {
        if (row == null) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }

    private String getcsv(String[] row, int col) {
        if (row == null || col >= row.length) return "";
        return row[col].trim().replace("\"", "");
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private int findStudentStartRow(Sheet sheet, DataFormatter formatter) {
        for (int i = 0; i < 20; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String cellValue = getCell(row, 1, formatter).toLowerCase();
            if (cellValue.contains("презиме") || cellValue.contains("prezime")) {
                return i + 1;
            }
        }
        return 7;
    }

    private String generateEmail(String firstName, String lastName) {
        String first = normalizeForEmail(firstName.split(" ")[0]);
        String last  = normalizeForEmail(lastName.split(" ")[0]);
        return first + "." + last + "@" + studentEmailDomain;
    }

    private String normalizeForEmail(String input) {
        String latin = toLatin(input);
        String normalized = Normalizer.normalize(latin, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String extractAcademicYear(String title) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{4}");
            java.util.regex.Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group());
                int nextYear = (year + 1) % 100;
                return year + "/" + String.format("%02d", nextYear);
            }
        } catch (Exception e) {
            // ignoriši
        }
        return "Nepoznato";
    }

    private String toLatin(String input) {
        if (input == null) return "";
        return input
                .replace("А", "A").replace("Б", "B").replace("В", "V").replace("Г", "G")
                .replace("Д", "D").replace("Ђ", "Dj").replace("Е", "E").replace("Ж", "Z")
                .replace("З", "Z").replace("И", "I").replace("Ј", "J").replace("К", "K")
                .replace("Л", "L").replace("Љ", "Lj").replace("М", "M").replace("Н", "N")
                .replace("Њ", "Nj").replace("О", "O").replace("П", "P").replace("Р", "R")
                .replace("С", "S").replace("Т", "T").replace("Ћ", "C").replace("У", "U")
                .replace("Ф", "F").replace("Х", "H").replace("Ц", "C").replace("Ч", "C")
                .replace("Џ", "Dz").replace("Ш", "S")
                .replace("а", "a").replace("б", "b").replace("в", "v").replace("г", "g")
                .replace("д", "d").replace("ђ", "dj").replace("е", "e").replace("ж", "z")
                .replace("з", "z").replace("и", "i").replace("ј", "j").replace("к", "k")
                .replace("л", "l").replace("љ", "lj").replace("м", "m").replace("н", "n")
                .replace("њ", "nj").replace("о", "o").replace("п", "p").replace("р", "r")
                .replace("с", "s").replace("т", "t").replace("ћ", "c").replace("у", "u")
                .replace("ф", "f").replace("х", "h").replace("ц", "c").replace("č", "c")
                .replace("ć", "c").replace("ž", "z").replace("š", "s").replace("đ", "dj")
                .replace("Đ", "Dj")
                .replace("ч", "c").replace("џ", "dz").replace("ш", "s");
    }
}
