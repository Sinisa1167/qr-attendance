package com.qrattendance.backend.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.qrattendance.backend.model.Attendance;
import com.qrattendance.backend.model.Session;
import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final AttendanceService attendanceService;

    private BaseFont getCyrillicBaseFont() throws DocumentException, IOException {
        String[] paths = {
            "/usr/share/fonts/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/freefont/FreeSans.ttf"
        };
        for (String path : paths) {
            try {
                if (new File(path).exists()) {
                    return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                }
            } catch (Exception ignored) {}
        }
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
    }

    private Map<String, Long> calculateUniqueAttendance(List<Session> closedSessions) {
        return closedSessions.stream()
                .flatMap(s -> attendanceService.getAttendanceForSession(s).stream())
                .collect(Collectors.groupingBy(
                        a -> a.getStudent().getId(),
                        Collectors.mapping(a -> a.getSession().getId(),
                        Collectors.collectingAndThen(Collectors.toSet(), Set::size))
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().longValue()));
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    // --- EXCEL EXPORT ---

    @Transactional
    public byte[] exportSessionToXlsx(Session session) throws IOException {
        List<Attendance> attendanceList = attendanceService.getAttendanceForSession(session);
        Subject subject = session.getSubject();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Prisustvo");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            int rowNum = 0;
            createInfoRow(sheet, rowNum++, "Predmet:", safe(subject.getName()));
            createInfoRow(sheet, rowNum++, "Sifra:", safe(subject.getCode()));
            createInfoRow(sheet, rowNum++, "Studijski program:", safe(subject.getStudyProgram()));
            createInfoRow(sheet, rowNum++, "Tip studija:", safe(subject.getStudyType()));
            createInfoRow(sheet, rowNum++, "Godina studija:", String.valueOf(subject.getStudyYear()));
            createInfoRow(sheet, rowNum++, "Semestar:", String.valueOf(subject.getSemester()));
            createInfoRow(sheet, rowNum++, "Oblik nastave:", safe(subject.getTeachingType()));
            createInfoRow(sheet, rowNum++, "Grupa:", safe(subject.getGroupName()));
            createInfoRow(sheet, rowNum++, "Nastavnik:", subject.getCreatedBy() != null ?
                    subject.getCreatedBy().getFirstName() + " " + subject.getCreatedBy().getLastName() : "");
            createInfoRow(sheet, rowNum++, "Akademska godina:", safe(subject.getAcademicYear()));
            createInfoRow(sheet, rowNum++, "Datum sesije:", session.getDate().toString());
            createInfoRow(sheet, rowNum++, "Tip aktivnosti:", session.getActivityType() != null ?
                    session.getActivityType().toString().replace("_", " ") : "");
            rowNum++;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Email", "Vrijeme prijave"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int counter = 1;
            for (Attendance a : attendanceList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(counter++);
                row.createCell(1).setCellValue(safe(a.getStudent().getFirstName()));
                row.createCell(2).setCellValue(safe(a.getStudent().getLastName()));
                row.createCell(3).setCellValue(safe(a.getStudent().getIndexNumber()));
                row.createCell(4).setCellValue(safe(a.getStudent().getEmail()));
                row.createCell(5).setCellValue(a.getCheckInTime() != null ?
                        a.getCheckInTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) : "/");
            }

            // Fiksne širine kolona
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            sheet.setColumnWidth(2, 25 * 256);
            sheet.setColumnWidth(3, 18 * 256);
            sheet.setColumnWidth(4, 40 * 256);
            sheet.setColumnWidth(5, 25 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public byte[] exportSubjectToXlsx(Subject subject, List<Session> sessions) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Kumulativno prisustvo");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            List<Session> closedSessions = sessions.stream()
                    .filter(s -> s.getStatus() == Session.SessionStatus.CLOSED)
                    .collect(Collectors.toList());

            long totalSessions = closedSessions.size();
            Map<String, Long> attendanceCount = calculateUniqueAttendance(closedSessions);

            int rowNum = 0;
            createInfoRow(sheet, rowNum++, "Predmet:", safe(subject.getName()));
            createInfoRow(sheet, rowNum++, "Sifra:", safe(subject.getCode()));
            createInfoRow(sheet, rowNum++, "Studijski program:", safe(subject.getStudyProgram()));
            createInfoRow(sheet, rowNum++, "Tip studija:", safe(subject.getStudyType()));
            createInfoRow(sheet, rowNum++, "Godina studija:", String.valueOf(subject.getStudyYear()));
            createInfoRow(sheet, rowNum++, "Semestar:", String.valueOf(subject.getSemester()));
            createInfoRow(sheet, rowNum++, "Oblik nastave:", safe(subject.getTeachingType()));
            createInfoRow(sheet, rowNum++, "Grupa:", safe(subject.getGroupName()));
            createInfoRow(sheet, rowNum++, "Nastavnik:", subject.getCreatedBy() != null ?
                    subject.getCreatedBy().getFirstName() + " " + subject.getCreatedBy().getLastName() : "");
            createInfoRow(sheet, rowNum++, "Akademska godina:", safe(subject.getAcademicYear()));
            createInfoRow(sheet, rowNum++, "Ukupno sesija:", String.valueOf(totalSessions));
            rowNum++;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Email", "Dolasci", "Izostanci", "%"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<User> students = new ArrayList<>(subject.getStudents());
            students.sort((a, b) -> Long.compare(
                    attendanceCount.getOrDefault(b.getId(), 0L),
                    attendanceCount.getOrDefault(a.getId(), 0L)));

            int counter = 1;
            for (User student : students) {
                long dolasci = attendanceCount.getOrDefault(student.getId(), 0L);
                long izostanci = Math.max(0, totalSessions - dolasci);
                double postotak = totalSessions > 0 ? (dolasci * 100.0 / totalSessions) : 0;

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(counter++);
                row.createCell(1).setCellValue(safe(student.getFirstName()));
                row.createCell(2).setCellValue(safe(student.getLastName()));
                row.createCell(3).setCellValue(safe(student.getIndexNumber()));
                row.createCell(4).setCellValue(safe(student.getEmail()));

                Cell c5 = row.createCell(5); c5.setCellValue(dolasci); c5.setCellStyle(centerStyle);
                Cell c6 = row.createCell(6); c6.setCellValue(izostanci); c6.setCellStyle(centerStyle);
                Cell c7 = row.createCell(7); c7.setCellValue(String.format("%.1f%%", postotak)); c7.setCellStyle(centerStyle);
            }

            // Fiksne širine kolona
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            sheet.setColumnWidth(2, 25 * 256);
            sheet.setColumnWidth(3, 18 * 256);
            sheet.setColumnWidth(4, 40 * 256);
            sheet.setColumnWidth(5, 12 * 256);
            sheet.setColumnWidth(6, 12 * 256);
            sheet.setColumnWidth(7, 12 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // --- PDF EXPORT ---

    @Transactional
    public byte[] exportSessionToPdf(Session session) throws DocumentException, IOException {
        List<Attendance> attendanceList = attendanceService.getAttendanceForSession(session);
        Subject subject = session.getSubject();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont bf = getCyrillicBaseFont();
        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(bf, 14, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font boldFont = new com.itextpdf.text.Font(bf, 10, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(bf, 10);
        com.itextpdf.text.Font smallFont = new com.itextpdf.text.Font(bf, 9);

        document.add(new Paragraph("Izvjestaj o prisustvu", titleFont));
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Predmet: " + safe(subject.getName()), boldFont));
        document.add(new Paragraph("Sifra: " + safe(subject.getCode()), normalFont));
        document.add(new Paragraph("Studijski program: " + safe(subject.getStudyProgram()), normalFont));
        document.add(new Paragraph("Tip studija: " + safe(subject.getStudyType()), normalFont));
        document.add(new Paragraph("Godina studija: " + subject.getStudyYear(), normalFont));
        document.add(new Paragraph("Semestar: " + subject.getSemester(), normalFont));
        document.add(new Paragraph("Oblik nastave: " + safe(subject.getTeachingType()), normalFont));
        document.add(new Paragraph("Grupa: " + safe(subject.getGroupName()), normalFont));
        document.add(new Paragraph("Nastavnik: " + (subject.getCreatedBy() != null ?
                subject.getCreatedBy().getFirstName() + " " + subject.getCreatedBy().getLastName() : ""), normalFont));
        document.add(new Paragraph("Akademska godina: " + safe(subject.getAcademicYear()), normalFont));
        document.add(new Paragraph("Datum sesije: " + session.getDate().toString(), normalFont));
        document.add(new Paragraph("Tip aktivnosti: " + (session.getActivityType() != null ?
                session.getActivityType().toString().replace("_", " ") : ""), normalFont));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2f, 2f, 2f, 3f, 2f});
        String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Email", "Vrijeme"};
        for (String h : headers) addPdfCell(table, h, boldFont, BaseColor.LIGHT_GRAY);

        int counter = 1;
        for (Attendance a : attendanceList) {
            addPdfCell(table, String.valueOf(counter++), smallFont, BaseColor.WHITE);
            addPdfCell(table, safe(a.getStudent().getFirstName()), smallFont, BaseColor.WHITE);
            addPdfCell(table, safe(a.getStudent().getLastName()), smallFont, BaseColor.WHITE);
            addPdfCell(table, safe(a.getStudent().getIndexNumber()), smallFont, BaseColor.WHITE);
            addPdfCell(table, safe(a.getStudent().getEmail()), smallFont, BaseColor.WHITE);
            addPdfCell(table, a.getCheckInTime() != null ?
                    a.getCheckInTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) : "/",
                    smallFont, BaseColor.WHITE);
        }

        document.add(table);
        document.close();
        return out.toByteArray();
    }

    @Transactional
    public byte[] exportSubjectToPdf(Subject subject, List<Session> sessions) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont bf = getCyrillicBaseFont();
        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(bf, 14, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font boldFont = new com.itextpdf.text.Font(bf, 10, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(bf, 10);
        com.itextpdf.text.Font smallFont = new com.itextpdf.text.Font(bf, 9);

        List<Session> closedSessions = sessions.stream()
                .filter(s -> s.getStatus() == Session.SessionStatus.CLOSED)
                .collect(Collectors.toList());
        long totalSessions = closedSessions.size();
        Map<String, Long> attendanceCount = calculateUniqueAttendance(closedSessions);

        document.add(new Paragraph("Kumulativna evidencija prisustva", titleFont));
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Predmet: " + safe(subject.getName()), boldFont));
        document.add(new Paragraph("Sifra: " + safe(subject.getCode()), normalFont));
        document.add(new Paragraph("Studijski program: " + safe(subject.getStudyProgram()), normalFont));
        document.add(new Paragraph("Tip studija: " + safe(subject.getStudyType()), normalFont));
        document.add(new Paragraph("Godina studija: " + subject.getStudyYear(), normalFont));
        document.add(new Paragraph("Semestar: " + subject.getSemester(), normalFont));
        document.add(new Paragraph("Oblik nastave: " + safe(subject.getTeachingType()), normalFont));
        document.add(new Paragraph("Grupa: " + safe(subject.getGroupName()), normalFont));
        document.add(new Paragraph("Nastavnik: " + (subject.getCreatedBy() != null ?
                subject.getCreatedBy().getFirstName() + " " + subject.getCreatedBy().getLastName() : ""), normalFont));
        document.add(new Paragraph("Akademska godina: " + safe(subject.getAcademicYear()), normalFont));
        document.add(new Paragraph("Ukupno odrzanih sesija: " + totalSessions, normalFont));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2f, 2f, 2f, 3f, 1.2f, 1.2f, 1.5f});
        String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Email", "Dol.", "Izo.", "%"};
        for (String h : headers) addPdfCell(table, h, boldFont, BaseColor.LIGHT_GRAY);

        List<User> students = new ArrayList<>(subject.getStudents());
        students.sort((a, b) -> Long.compare(
                attendanceCount.getOrDefault(b.getId(), 0L),
                attendanceCount.getOrDefault(a.getId(), 0L)));

        int counter = 1;
        for (User student : students) {
            long dolasci = attendanceCount.getOrDefault(student.getId(), 0L);
            long izostanci = Math.max(0, totalSessions - dolasci);
            double postotak = totalSessions > 0 ? (dolasci * 100.0 / totalSessions) : 0;

            addPdfCell(table, String.valueOf(counter++), smallFont, BaseColor.WHITE);
            addPdfCell(table, safe(student.getFirstName()), smallFont, BaseColor.WHITE);
            addPdfCell(table, safe(student.getLastName()), smallFont, BaseColor.WHITE);
            addPdfCell(table, safe(student.getIndexNumber()), smallFont, BaseColor.WHITE);
            addPdfCell(table, safe(student.getEmail()), smallFont, BaseColor.WHITE);
            addPdfCell(table, String.valueOf(dolasci), smallFont, BaseColor.WHITE);
            addPdfCell(table, String.valueOf(izostanci), smallFont, BaseColor.WHITE);
            addPdfCell(table, String.format("%.1f%%", postotak), smallFont, BaseColor.WHITE);
        }

        document.add(table);
        document.close();
        return out.toByteArray();
    }

    // --- HELPER METODE ---

    private void createInfoRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value : "");
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void addPdfCell(PdfPTable table, String text, com.itextpdf.text.Font font, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bgColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }
}