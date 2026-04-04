package com.qrattendance.backend.service;

import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelParserService {

    private final UserService userService;
    private final SubjectService subjectService;

    @Value("${app.student-email-domain}")
    private String studentEmailDomain;

    public Subject parseAndSave(MultipartFile file, User createdBy) throws IOException {

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            // metapodaci – fiksne kolone (template fakulteta)
            Row metaRow = sheet.getRow(4);
            if (metaRow == null) {
                throw new IllegalArgumentException("Neispravan Excel format – nema reda sa metapodacima (red 5).");
            }

            String subjectName = getCell(metaRow, 7, formatter);
            String subjectCode = getCell(metaRow, 9, formatter);
            String studyProgram = getCell(metaRow, 11, formatter);
            String studyType = getCell(metaRow, 13, formatter);
            String studyYearStr = getCell(metaRow, 15, formatter);
            String semesterStr = getCell(metaRow, 17, formatter);
            String teachingType = getCell(metaRow, 19, formatter);
            String groupName = getCell(metaRow, 21, formatter);

            Subject subject = new Subject();
            subject.setName(subjectName);
            subject.setCode(subjectCode);
            subject.setStudyProgram(studyProgram);
            subject.setStudyType(studyType);
            subject.setStudyYear(parseInt(studyYearStr));
            subject.setSemester(parseInt(semesterStr));
            subject.setTeachingType(teachingType);
            subject.setGroupName(groupName);
            subject.setCreatedBy(createdBy);

            Subject savedSubject = subjectService.save(subject);

            //  studenti – dinamicki pronalazak pocetka
            int startRow = findStudentStartRow(sheet, formatter);

            List<User> students = new ArrayList<>();

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String lastName = getCell(row, 1, formatter);
                String firstName = getCell(row, 2, formatter);
                String indexNumber = getCell(row, 3, formatter);

                if (indexNumber.isBlank()) break;                    // kraj liste studenata
                if (firstName.isBlank() || lastName.isBlank()) continue;

                String email = generateEmail(firstName, lastName);

                User student = userService.findByEmail(email)
                        .orElseGet(() -> createNewStudent(firstName, lastName, email));

                students.add(student);
            }

            savedSubject.setStudents(students);
            return subjectService.save(savedSubject);
        }
    }

    private String getCell(Row row, int col, DataFormatter formatter) {
        if (row == null) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
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

    private User createNewStudent(String firstName, String lastName, String email) {
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setRole(User.UserRole.STUDENT);
        return userService.save(newUser);
    }

    private String generateEmail(String firstName, String lastName) {
        String first = normalizeForEmail(firstName.split(" ")[0]);
        String last = normalizeForEmail(lastName.split(" ")[0]);
        return first + "." + last + "@" + studentEmailDomain;
    }

    private String normalizeForEmail(String input) {
        String latin = toLatin(input);
        String normalized = Normalizer.normalize(latin, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized.toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }

    private String toLatin(String input) {
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
                .replace("ф", "f").replace("х", "h").replace("ц", "c").replace("ч", "c")
                .replace("џ", "dz").replace("ш", "s");
    }
}