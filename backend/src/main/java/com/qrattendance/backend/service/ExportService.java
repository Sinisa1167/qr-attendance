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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final AttendanceService attendanceService;
    private static final String FONT_PATH = "fonts/ARIAL.ttf"; 

    private BaseFont getCyrillicBaseFont() throws DocumentException, IOException {
        try {
            return BaseFont.createFont(FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        }
    }
    
    private Map<String, Long> calculateUniqueAttendance(List<Session> closedSessions) {
        // Ključ: StudentID, Vrijednost: Broj jedinstvenih SessionID-eva na kojima je bio
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

    // --- EXCEL EXPORT ---

    public byte[] exportSessionToXlsx(Session session) throws IOException {
        List<Attendance> attendanceList = attendanceService.getAttendanceForSession(session);
        Subject subject = session.getSubject();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Prisustvo");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            int rowNum = 0;
            createInfoRow(sheet, rowNum++, "Predmet:", subject.getName());
            createInfoRow(sheet, rowNum++, "Datum sesije:", session.getDate().toString());
            rowNum++;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Vrijeme prijave"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int counter = 1;
            for (Attendance a : attendanceList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(counter++);
                row.createCell(1).setCellValue(a.getStudent().getFirstName());
                row.createCell(2).setCellValue(a.getStudent().getLastName());
                row.createCell(3).setCellValue(a.getStudent().getIndexNumber() != null ? a.getStudent().getIndexNumber() : "");
                row.createCell(4).setCellValue(a.getCheckInTime() != null ? a.getCheckInTime().toString() : "/");
            }

            for(int i=0; i<5; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

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
            createInfoRow(sheet, rowNum++, "Predmet:", subject.getName());
            createInfoRow(sheet, rowNum++, "Ukupno sesija:", String.valueOf(totalSessions));
            rowNum++;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Dolasci", "Izostanci", "%"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<User> students = new ArrayList<>(subject.getStudents());
            students.sort((a, b) -> Long.compare(attendanceCount.getOrDefault(b.getId(), 0L), attendanceCount.getOrDefault(a.getId(), 0L)));

            int counter = 1;
            for (User student : students) {
                long dolasci = attendanceCount.getOrDefault(student.getId(), 0L);
                long izostanci = Math.max(0, totalSessions - dolasci);
                double postotak = totalSessions > 0 ? (dolasci * 100.0 / totalSessions) : 0;

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(counter++);
                row.createCell(1).setCellValue(student.getFirstName());
                row.createCell(2).setCellValue(student.getLastName());
                row.createCell(3).setCellValue(student.getIndexNumber() != null ? student.getIndexNumber() : "");
                
                Cell c4 = row.createCell(4); c4.setCellValue(dolasci); c4.setCellStyle(centerStyle);
                Cell c5 = row.createCell(5); c5.setCellValue(izostanci); c5.setCellStyle(centerStyle);
                Cell c6 = row.createCell(6); c6.setCellValue(String.format("%.1f%%", postotak)); c6.setCellStyle(centerStyle);
            }

            for(int i=0; i<7; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // --- PDF EXPORT ---

    public byte[] exportSessionToPdf(Session session) throws DocumentException, IOException {
        List<Attendance> attendanceList = attendanceService.getAttendanceForSession(session);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont bf = getCyrillicBaseFont();
        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(bf, 14, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(bf, 10);
        com.itextpdf.text.Font boldFont = new com.itextpdf.text.Font(bf, 10, com.itextpdf.text.Font.BOLD);

        document.add(new Paragraph("Izvještaj o prisustvu - " + session.getSubject().getName(), titleFont));
        document.add(new Paragraph("Datum sesije: " + session.getDate().toString(), normalFont));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        String[] headers = {"Br.", "Ime i Prezime", "Indeks", "Email"};
        for (String h : headers) addPdfCell(table, h, boldFont, BaseColor.LIGHT_GRAY);

        int counter = 1;
        for (Attendance a : attendanceList) {
            addPdfCell(table, String.valueOf(counter++), normalFont, BaseColor.WHITE);
            addPdfCell(table, a.getStudent().getFirstName() + " " + a.getStudent().getLastName(), normalFont, BaseColor.WHITE);
            addPdfCell(table, a.getStudent().getIndexNumber(), normalFont, BaseColor.WHITE);
            addPdfCell(table, a.getStudent().getEmail(), normalFont, BaseColor.WHITE);
        }

        document.add(table);
        document.close();
        return out.toByteArray();
    }

    public byte[] exportSubjectToPdf(Subject subject, List<Session> sessions) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont bf = getCyrillicBaseFont();
        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(bf, 14, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(bf, 10);
        com.itextpdf.text.Font boldFont = new com.itextpdf.text.Font(bf, 10, com.itextpdf.text.Font.BOLD);

        List<Session> closedSessions = sessions.stream()
                .filter(s -> s.getStatus() == Session.SessionStatus.CLOSED)
                .collect(Collectors.toList());
        long totalSessions = closedSessions.size();
        Map<String, Long> attendanceCount = calculateUniqueAttendance(closedSessions);

        document.add(new Paragraph("Kumulativna evidencija: " + subject.getName(), titleFont));
        document.add(new Paragraph("Ukupno održanih sesija: " + totalSessions, normalFont));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2.5f, 2.5f, 2f, 1.2f, 1.2f, 1.5f});

        String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Dol.", "Izo.", "%"};
        for (String h : headers) addPdfCell(table, h, boldFont, BaseColor.LIGHT_GRAY);

        List<User> students = new ArrayList<>(subject.getStudents());
        students.sort((a, b) -> Long.compare(attendanceCount.getOrDefault(b.getId(), 0L), attendanceCount.getOrDefault(a.getId(), 0L)));

        int counter = 1;
        for (User student : students) {
            long dolasci = attendanceCount.getOrDefault(student.getId(), 0L);
            long izostanci = Math.max(0, totalSessions - dolasci);
            double postotak = totalSessions > 0 ? (dolasci * 100.0 / totalSessions) : 0;

            addPdfCell(table, String.valueOf(counter++), normalFont, BaseColor.WHITE);
            addPdfCell(table, student.getFirstName(), normalFont, BaseColor.WHITE);
            addPdfCell(table, student.getLastName(), normalFont, BaseColor.WHITE);
            addPdfCell(table, student.getIndexNumber() != null ? student.getIndexNumber() : "", normalFont, BaseColor.WHITE);
            addPdfCell(table, String.valueOf(dolasci), normalFont, BaseColor.WHITE);
            addPdfCell(table, String.valueOf(izostanci), normalFont, BaseColor.WHITE);
            addPdfCell(table, String.format("%.1f%%", postotak), normalFont, BaseColor.WHITE);
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