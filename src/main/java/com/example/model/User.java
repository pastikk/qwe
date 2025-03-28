package com.example.model;

import lombok.Data;
import java.time.LocalDate;

@Data
public class User {
    private String fullName;
    private LocalDate birthDate;
}