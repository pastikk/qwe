package com.example.model;

import java.time.LocalDate;

public class ProcessedUser {
    private String fullName;
    private LocalDate birthDate;
    private int ageYears;
    private int ageMonths;
    private String status;
    private String errorDetails;

    // Конструктор по умолчанию
    public ProcessedUser() {
        this.fullName = "";
        this.status = "";
        this.errorDetails = "";
    }

    // Геттеры и сеттеры
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName != null ? fullName : "";
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public int getAgeYears() {
        return ageYears;
    }

    public void setAgeYears(int ageYears) {
        this.ageYears = ageYears;
    }

    public int getAgeMonths() {
        return ageMonths;
    }

    public void setAgeMonths(int ageMonths) {
        this.ageMonths = ageMonths;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status != null ? status : "";
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails != null ? errorDetails : "";
    }
}