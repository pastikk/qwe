package com.example.controller;

import com.example.model.FileProcessingTask;
import com.example.service.FileProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller
public class FileUploadController {
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    private final FileProcessingService fileProcessingService;

    @Autowired
    public FileUploadController(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    @GetMapping("/")
    public String uploadForm(Model model) {
        return "upload";
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                throw new IOException("Файл пустой");
            }

            String taskId = fileProcessingService.processFile(file);
            return "redirect:/status?taskId=" + taskId;
        } catch (Exception e) {
            logger.error("Ошибка при загрузке файла", e);
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка загрузки: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/status")
    public String statusPage(@RequestParam String taskId, Model model) throws IOException {
        FileProcessingTask task = fileProcessingService.getTask(taskId);

        if (task == null) {
            throw new IllegalArgumentException("Задача не найдена");
        }

        model.addAttribute("task", task);
        model.addAttribute("taskId", taskId);

        if (task.getStatus() == FileProcessingTask.TaskStatus.FAILED) {
            model.addAttribute("error", task.getErrorDetails());
        } else if (task.getStatus() == FileProcessingTask.TaskStatus.COMPLETED) {
            try {
                model.addAttribute("processedData",
                        fileProcessingService.getProcessedData(taskId));
            } catch (Exception e) {
                model.addAttribute("error", "Ошибка загрузки результатов: " + e.getMessage());
            }
        }

        return "status";
    }

    @GetMapping("/status/check")
    @ResponseBody
    public Map<String, Object> checkStatus(@RequestParam String taskId) {
        FileProcessingTask task = fileProcessingService.getTask(taskId);
        Map<String, Object> response = new HashMap<>();
        response.put("status", task.getStatus().toString());
        response.put("completed", task.getStatus() == FileProcessingTask.TaskStatus.COMPLETED);
        return response;
    }

    @GetMapping("/download/pdf")
    public void downloadPdfReport(@RequestParam String taskId,
                                  HttpServletResponse response) throws IOException {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"report_" + taskId + ".pdf\"");
            fileProcessingService.generatePdfReport(taskId, response.getOutputStream());
        } catch (Exception e) {
            logger.error("Ошибка при скачивании PDF", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/view/pdf")
    public ResponseEntity<Resource> viewPdfReport(@RequestParam String taskId) {
        try {
            logger.info("Попытка просмотра PDF для задачи: {}", taskId);
            Resource resource = fileProcessingService.loadPdfReport(taskId);

            if (!resource.exists()) {
                logger.error("PDF не найден для задачи: {}", taskId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"report_" + taskId + ".pdf\"")
                    .body(resource);
        } catch (Exception e) {
            logger.error("Ошибка при просмотре PDF для задачи: " + taskId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/error")
    public String handleError(Model model) {
        model.addAttribute("error", "Произошла непредвиденная ошибка");
        return "error";
    }
}