package com.example.model;

import java.time.Instant;

public class FileProcessingTask {
    private String taskId;
    private String originalFilename;
    private String processedFilename;
    private TaskStatus status;
    private Instant createdDate;
    private String errorDetails;

    public enum TaskStatus {
        QUEUED, PROCESSING, COMPLETED, FAILED, PENDING
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getProcessedFilename() {
        return processedFilename;
    }

    public void setProcessedFilename(String processedFilename) {
        this.processedFilename = processedFilename;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}