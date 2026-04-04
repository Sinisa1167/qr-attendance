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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final AttendanceService attendanceService;
    private static final String FONT_PATH = "fonts/ARIAL.ttf"; 

    private BaseFont getCyrillicBaseFont() throws DocumentException, IOException {
        return BaseFont.createFont(FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }

    // EXCEL EXPORT

    public byte[] exportSessionToXlsx(Session session) throws IOException {
        List<Attendance> attendanceList = attendanceService.getAttendanceForSession(session);
        Subject subject = session.getSubject();
        User nastavnik = subject.getCreatedBy();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Prisustvo");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle infoStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            infoStyle.setFont(boldFont);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            int rowNum = 0;
            createInfoRow(sheet, rowNum++, "Predmet:", subject.getName(), infoStyle);
            createInfoRow(sheet, rowNum++, "Šifra:", subject.getCode(), infoStyle);
            createInfoRow(sheet, rowNum++, "Studijski program:", subject.getStudyProgram(), infoStyle);
            createInfoRow(sheet, rowNum++, "Oblik nastave:", subject.getTeachingType(), infoStyle);
            createInfoRow(sheet, rowNum++, "Grupa:", subject.getGroupName(), infoStyle);
            createInfoRow(sheet, rowNum++, "Semestar:", String.valueOf(subject.getSemester()), infoStyle);
            createInfoRow(sheet, rowNum++, "Godina studija:", String.valueOf(subject.getStudyYear()), infoStyle);
            createInfoRow(sheet, rowNum++, "Nastavnik:", nastavnik.getFirstName() + " " + nastavnik.getLastName(), infoStyle);
            createInfoRow(sheet, rowNum++, "Datum sesije:", session.getDate().toString(), infoStyle);
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
                
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(counter++);
                cell0.setCellStyle(centerStyle);
                
                row.createCell(1).setCellValue(a.getStudent().getFirstName());
                row.createCell(2).setCellValue(a.getStudent().getLastName());
                row.createCell(3).setCellValue(a.getStudent().getIndexNumber() != null ? a.getStudent().getIndexNumber() : "");
                row.createCell(4).setCellValue(a.getStudent().getEmail());
                row.createCell(5).setCellValue(a.getCheckInTime().toString());
            }

            sheet.setColumnWidth(0, 18 * 256); 
            sheet.setColumnWidth(1, 18 * 256); // Ime
            sheet.setColumnWidth(2, 22 * 256); // Prezime
            sheet.setColumnWidth(3, 15 * 256); // Indeks
            sheet.setColumnWidth(4, 40 * 256); // Email
            sheet.setColumnWidth(5, 25 * 256); // Vrijeme

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

            long totalSessions = sessions.stream()
                    .filter(s -> s.getStatus() == Session.SessionStatus.CLOSED).count();

            int rowNum = 0;
            createInfoRow(sheet, rowNum++, "Predmet:", subject.getName(), null);
            createInfoRow(sheet, rowNum++, "Šifra:", subject.getCode(), null);
            createInfoRow(sheet, rowNum++, "Studijski program:", subject.getStudyProgram(), null);
            createInfoRow(sheet, rowNum++, "Nastavnik:", 
                    subject.getCreatedBy().getFirstName() + " " + subject.getCreatedBy().getLastName(), null);
            createInfoRow(sheet, rowNum++, "Ukupno sesija:", String.valueOf(totalSessions), null);
            rowNum++;

            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Dolasci", "Izostanci", "%"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            Map<String, Long> attendanceCount = sessions.stream()
                    .flatMap(s -> attendanceService.getAttendanceForSession(s).stream())
                    .collect(Collectors.groupingBy(a -> a.getStudent().getId(), Collectors.counting()));

            List<User> students = new ArrayList<>(subject.getStudents());
            students.sort((a, b) -> Long.compare(attendanceCount.getOrDefault(b.getId(), 0L), attendanceCount.getOrDefault(a.getId(), 0L)));

            int counter = 1;
            for (User student : students) {
                long dolasci = attendanceCount.getOrDefault(student.getId(), 0L);
                long izostanci = totalSessions - dolasci;
                double postotak = totalSessions > 0 ? (dolasci * 100.0 / totalSessions) : 0;

                Row row = sheet.createRow(rowNum++);
                
                Cell c0 = row.createCell(0);
                c0.setCellValue(counter++);
                c0.setCellStyle(centerStyle);

                row.createCell(1).setCellValue(student.getFirstName());
                row.createCell(2).setCellValue(student.getLastName());
                row.createCell(3).setCellValue(student.getIndexNumber() != null ? student.getIndexNumber() : "");
                
                Cell c4 = row.createCell(4);
                c4.setCellValue(dolasci);
                c4.setCellStyle(centerStyle);
                
                Cell c5 = row.createCell(5);
                c5.setCellValue(izostanci);
                c5.setCellStyle(centerStyle);
                
                Cell c6 = row.createCell(6);
                c6.setCellValue(String.format("%.1f%%", postotak));
                c6.setCellStyle(centerStyle);
            }

            // Podešavanje širina za kumulativni export
            sheet.setColumnWidth(0, 18 * 256); // Br.
            sheet.setColumnWidth(1, 18 * 256); // Ime
            sheet.setColumnWidth(2, 22 * 256); // Prezime
            sheet.setColumnWidth(3, 15 * 256); // Indeks
            sheet.setColumnWidth(4, 12 * 256); // Dolasci
            sheet.setColumnWidth(5, 12 * 256); // Izostanci
            sheet.setColumnWidth(6, 12 * 256); // Postotak

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // PDF EXPORT

    public byte[] exportSessionToPdf(Session session) throws DocumentException, IOException {
        List<Attendance> attendanceList = attendanceService.getAttendanceForSession(session);
        Subject subject = session.getSubject();
        User nastavnik = subject.getCreatedBy();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        BaseFont bf = getCyrillicBaseFont();
        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(bf, 14, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(bf, 10);
        com.itextpdf.text.Font boldFont = new com.itextpdf.text.Font(bf, 10, com.itextpdf.text.Font.BOLD);

        Paragraph title = new Paragraph("Evidencija prisustva", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" "));

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(10);
        infoTable.setWidths(new float[]{2.5f, 4f});

        addInfoCell(infoTable, "Predmet:", subject.getName(), boldFont, normalFont);
        addInfoCell(infoTable, "Sifra:", subject.getCode(), boldFont, normalFont);
        addInfoCell(infoTable, "Studijski program:", subject.getStudyProgram(), boldFont, normalFont);
        addInfoCell(infoTable, "Tip studija:", subject.getStudyType(), boldFont, normalFont);
        addInfoCell(infoTable, "Godina studija:", String.valueOf(subject.getStudyYear()), boldFont, normalFont);
        addInfoCell(infoTable, "Semestar:", String.valueOf(subject.getSemester()), boldFont, normalFont);
        addInfoCell(infoTable, "Oblik nastave:", subject.getTeachingType(), boldFont, normalFont);
        addInfoCell(infoTable, "Grupa:", subject.getGroupName(), boldFont, normalFont);
        addInfoCell(infoTable, "Nastavnik:", nastavnik.getFirstName() + " " + nastavnik.getLastName(), boldFont, normalFont);
        addInfoCell(infoTable, "Datum sesije:", session.getDate().toString(), boldFont, normalFont);
        document.add(infoTable);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2.5f, 2.5f, 2f, 3f});

        String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Email"};
        for (String h : headers) addPdfCell(table, h, boldFont, BaseColor.LIGHT_GRAY);

        int counter = 1;
        for (Attendance a : attendanceList) {
            User s = a.getStudent();
            addPdfCell(table, String.valueOf(counter++), normalFont, BaseColor.WHITE);
            addPdfCell(table, s.getFirstName(), normalFont, BaseColor.WHITE);
            addPdfCell(table, s.getLastName(), normalFont, BaseColor.WHITE);
            addPdfCell(table, s.getIndexNumber() != null ? s.getIndexNumber() : "", normalFont, BaseColor.WHITE);
            addPdfCell(table, s.getEmail(), normalFont, BaseColor.WHITE);
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

        User nastavnik = subject.getCreatedBy();

        Paragraph title = new Paragraph("Kumulativna evidencija prisustva", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" "));

        long totalSessions = sessions.stream()
                .filter(s -> s.getStatus() == Session.SessionStatus.CLOSED).count();

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(10);
        infoTable.setWidths(new float[]{2.5f, 4f});

        addInfoCell(infoTable, "Predmet:", subject.getName(), boldFont, normalFont);
        addInfoCell(infoTable, "Sifra:", subject.getCode(), boldFont, normalFont);
        addInfoCell(infoTable, "Studijski program:", subject.getStudyProgram(), boldFont, normalFont);
        addInfoCell(infoTable, "Godina studija:", String.valueOf(subject.getStudyYear()), boldFont, normalFont);
        addInfoCell(infoTable, "Nastavnik:", nastavnik.getFirstName() + " " + nastavnik.getLastName(), boldFont, normalFont);
        addInfoCell(infoTable, "Ukupno sesija:", String.valueOf(totalSessions), boldFont, normalFont);
        document.add(infoTable);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2.5f, 2.5f, 2f, 1.2f, 1.2f, 1.2f});

        String[] headers = {"Br.", "Ime", "Prezime", "Indeks", "Dol.", "Izo.", "%"};
        for (String h : headers) addPdfCell(table, h, boldFont, BaseColor.LIGHT_GRAY);

        Map<String, Long> attendanceCount = sessions.stream()
                .flatMap(s -> attendanceService.getAttendanceForSession(s).stream())
                .collect(Collectors.groupingBy(a -> a.getStudent().getId(), Collectors.counting()));

        List<User> students = new ArrayList<>(subject.getStudents());
        students.sort((a, b) -> Long.compare(attendanceCount.getOrDefault(b.getId(), 0L), attendanceCount.getOrDefault(a.getId(), 0L)));

        int counter = 1;
        for (User student : students) {
            long dolasci = attendanceCount.getOrDefault(student.getId(), 0L);
            long izostanci = totalSessions - dolasci;
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

    // helper

    private void createInfoRow(Sheet sheet, int rowNum, String label, String value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        Cell c1 = row.createCell(0);
        c1.setCellValue(label);
        if (style != null) c1.setCellStyle(style);
        row.createCell(1).setCellValue(value != null ? value : "");
    }

    private void addInfoCell(PdfPTable table, String label, String value,
                             com.itextpdf.text.Font labelFont, com.itextpdf.text.Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(PdfPCell.NO_BORDER);
        labelCell.setPadding(3);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "", valueFont));
        valueCell.setBorder(PdfPCell.NO_BORDER);
        valueCell.setPadding(3);
        table.addCell(valueCell);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void addPdfCell(PdfPTable table, String text, com.itextpdf.text.Font font, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }
}