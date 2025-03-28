package com.example.service;

import com.example.model.FileProcessingTask;
import com.example.model.ProcessedUser;
import com.itextpdf.html2pdf.HtmlConverter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class FileProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(FileProcessingService.class);
    private final Map<String, FileProcessingTask> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final String uploadDir = "uploads";

    public FileProcessingService() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
    }

    public Resource loadPdfReport(String taskId) throws IOException {
        Path filePath = Paths.get(uploadDir, "report_" + taskId + ".pdf")
                .toAbsolutePath()
                .normalize();

        // Защита от Path Traversal
        if (!filePath.startsWith(Paths.get(uploadDir).toAbsolutePath())) {
            throw new SecurityException("Попытка доступа к файлу вне рабочей директории");
        }

        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("PDF не найден");
        }

        if (Files.size(filePath) == 0) {
            throw new IOException("PDF файл пустой");
        }

        return new UrlResource(filePath.toUri());
    }

    public String processFile(MultipartFile file) throws IOException {
        validateFile(file);

        String taskId = UUID.randomUUID().toString();
        String savedFilePath = uploadDir + "/" + taskId + "_" + file.getOriginalFilename();

        saveUploadedFile(file, savedFilePath);

        FileProcessingTask task = new FileProcessingTask();
        task.setTaskId(taskId);
        task.setOriginalFilename(file.getOriginalFilename());
        task.setStatus(FileProcessingTask.TaskStatus.PENDING);
        tasks.put(taskId, task);

        executorService.submit(() -> processFileAsync(savedFilePath, task));

        return taskId;
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("File is empty");
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw new IOException("Only .xlsx files are supported");
        }
    }

    private void saveUploadedFile(MultipartFile file, String filePath) throws IOException {
        Path destination = Paths.get(filePath);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private void processFileAsync(String inputFilePath, FileProcessingTask task) {
        try {
            task.setStatus(FileProcessingTask.TaskStatus.PROCESSING);
            String processedFilePath = processExcelFile(inputFilePath, task.getTaskId());
            task.setProcessedFilename(processedFilePath);
            task.setStatus(FileProcessingTask.TaskStatus.COMPLETED);
            logger.info("File processed successfully: {}", processedFilePath);
        } catch (Exception e) {
            logger.error("Error processing file", e);
            task.setStatus(FileProcessingTask.TaskStatus.FAILED);
            task.setErrorDetails(e.getMessage());
        }
    }

    private String processExcelFile(String inputFilePath, String taskId) throws IOException {
        String outputFilePath = uploadDir + "/processed_" + taskId + ".xlsx";
        String pdfFilePath = uploadDir + "/report_" + taskId + ".pdf";
        logger.info("Обработка Excel файла: {}", inputFilePath);


        List<ProcessedUser> processedUsers = new ArrayList<>();

        try (InputStream is = new FileInputStream(inputFilePath);
             Workbook workbook = WorkbookFactory.create(is);
             Workbook outputWorkbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.getSheetAt(0);
            Sheet outputSheet = outputWorkbook.createSheet("Processed Data");

            createHeaderRow(outputSheet);

            // Обработка данных
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                ProcessedUser user = processUserRow(row);
                processedUsers.add(user);
                createDataRow(outputSheet, i, user);
            }

            // Сохранение обработанного файла
            try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
                outputWorkbook.write(fos);
            }

            // Генерация PDF отчета
            try (FileOutputStream pdfOut = new FileOutputStream(pdfFilePath)) {
                String html = generateHtmlReport(processedUsers);
                HtmlConverter.convertToPdf(html, pdfOut);
            }

        } catch (Exception e) {
            logger.error("Ошибка обработки Excel файла", e);
            throw new IOException("Ошибка обработки Excel файла: " + e.getMessage(), e);
        }

        return outputFilePath;
    }

    private void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ФИО");
        headerRow.createCell(1).setCellValue("Дата рождения");
        headerRow.createCell(2).setCellValue("Возраст в годах");
        headerRow.createCell(3).setCellValue("Возраст в месяцах");
        headerRow.createCell(4).setCellValue("Статус");
        headerRow.createCell(5).setCellValue("Детализация ошибки");
    }

    private void createDataRow(Sheet sheet, int rowNum, ProcessedUser user) {
        Row row = sheet.createRow(rowNum);

        // ФИО
        row.createCell(0).setCellValue(user.getFullName());

        // Дата рождения (с проверкой на null)
        String birthDateStr = user.getBirthDate() != null ? user.getBirthDate().toString() : "";
        row.createCell(1).setCellValue(birthDateStr);

        // Возраст
        row.createCell(2).setCellValue(user.getAgeYears());
        row.createCell(3).setCellValue(user.getAgeMonths());

        // Статус и ошибки
        row.createCell(4).setCellValue(user.getStatus());
        row.createCell(5).setCellValue(user.getErrorDetails() != null ? user.getErrorDetails() : "");
    }

    public List<ProcessedUser> getProcessedData(String taskId) throws IOException {
        List<ProcessedUser> data = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new File(uploadDir + "/processed_" + taskId + ".xlsx"))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                data.add(createProcessedUserFromRow(row));
            }
        }
        return data;
    }

    private ProcessedUser createProcessedUserFromRow(Row row) {
        ProcessedUser user = new ProcessedUser();
        try {
            user.setFullName(row.getCell(0) != null ? row.getCell(0).getStringCellValue() : "");

            Cell dateCell = row.getCell(1);
            if (dateCell != null) {
                if (dateCell.getCellType() == CellType.NUMERIC) {
                    user.setBirthDate(dateCell.getLocalDateTimeCellValue().toLocalDate());
                } else {
                    user.setBirthDate(LocalDate.parse(dateCell.getStringCellValue()));
                }
            }

            user.setAgeYears((int)row.getCell(2).getNumericCellValue());
            user.setAgeMonths((int)row.getCell(3).getNumericCellValue());
            user.setStatus(row.getCell(4).getStringCellValue());
            user.setErrorDetails(row.getCell(5).getStringCellValue());
        } catch (Exception e) {
            logger.error("Ошибка чтения строки данных", e);
            user.setStatus("Ошибка");
            user.setErrorDetails("Некорректные данные в строке");
        }
        return user;
    }

    public void generatePdfReport(String taskId, OutputStream outputStream) throws IOException {
        try {
            logger.info("Начало генерации PDF для задачи {}", taskId);
            List<ProcessedUser> processedData = getProcessedData(taskId);

            if (processedData.isEmpty()) {
                logger.error("Нет данных для генерации PDF");
                throw new IOException("Нет данных для отчёта");
            }

            String htmlContent = generateHtmlReport(processedData);
            logger.debug("Сгенерирован HTML контент: {} символов", htmlContent.length());

            HtmlConverter.convertToPdf(htmlContent, outputStream);
            logger.info("PDF успешно сгенерирован");

        } catch (Exception e) {
            logger.error("Ошибка генерации PDF для задачи " + taskId, e);
            throw new IOException("Не удалось создать PDF: " + e.getMessage(), e);
        }
    }

    private String generateHtmlReport(List<ProcessedUser> processedData) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
                .append("<title>Отчёт</title><style>body{font-family:Arial,sans-serif;margin:20px;}")
                .append("table{width:100%;border-collapse:collapse;}th,td{border:1px solid #ddd;padding:8px;}")
                .append("th{background-color:#f2f2f2;}.status-ok{color:green;}.status-error{color:red;}</style></head>")
                .append("<body><h1>Отчёт по обработке данных</h1><table><thead><tr>")
                .append("<th>ФИО</th><th>Дата рождения</th><th>Возраст (лет)</th>")
                .append("<th>Возраст (мес.)</th><th>Статус</th><th>Детализация</th></tr></thead><tbody>");

        for (ProcessedUser user : processedData) {
            String statusClass = "Ок".equals(user.getStatus()) ? "status-ok" : "status-error";
            html.append("<tr><td>").append(user.getFullName()).append("</td>")
                    .append("<td>").append(user.getBirthDate()).append("</td>")
                    .append("<td>").append(user.getAgeYears()).append("</td>")
                    .append("<td>").append(user.getAgeMonths()).append("</td>")
                    .append("<td class=\"").append(statusClass).append("\">").append(user.getStatus()).append("</td>")
                    .append("<td>").append(user.getErrorDetails()).append("</td></tr>");
        }

        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    private ProcessedUser processUserRow(Row row) {
        ProcessedUser user = new ProcessedUser();
        try {
            // 1. Обработка ФИО (первая колонка)
            Cell nameCell = row.getCell(0);
            if (nameCell != null) {
                user.setFullName(nameCell.toString().trim());
            } else {
                user.setFullName("");
                user.setStatus("не ок");
                user.setErrorDetails("Отсутствует ФИО");
                return user;
            }

            // 2. Обработка даты рождения (вторая колонка)
            Cell dateCell = row.getCell(1);
            if (dateCell != null) {
                try {
                    LocalDate birthDate;
                    if (dateCell.getCellType() == CellType.NUMERIC) {
                        // Для числового формата даты Excel
                        birthDate = dateCell.getLocalDateTimeCellValue().toLocalDate();
                    } else {
                        // Для текстового формата (например, "1990-05-15")
                        birthDate = LocalDate.parse(dateCell.toString().trim());
                    }
                    user.setBirthDate(birthDate);

                    // 3. Расчет возраста
                    Period period = Period.between(birthDate, LocalDate.now());
                    user.setAgeYears(period.getYears());
                    user.setAgeMonths(period.getMonths());

                    // 4. Проверка корректности даты
                    if (birthDate.isAfter(LocalDate.now())) {
                        user.setStatus("не ок");
                        user.setErrorDetails("Дата рождения в будущем");
                    } else {
                        user.setStatus("Ок");
                    }
                } catch (Exception e) {
                    user.setStatus("не ок");
                    user.setErrorDetails("Некорректный формат даты: " + dateCell.toString());
                }
            } else {
                user.setStatus("не ок");
                user.setErrorDetails("Отсутствует дата рождения");
            }
        } catch (Exception e) {
            logger.error("Ошибка обработки строки", e);
            user.setStatus("не ок");
            user.setErrorDetails("Ошибка обработки данных: " + e.getMessage());
        }
        return user;
    }

    public FileProcessingTask getTask(String taskId) {
        return tasks.get(taskId);
    }
}