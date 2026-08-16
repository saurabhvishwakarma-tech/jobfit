package com.jobfit.resume.dto;

import java.time.LocalDate;

public record CertificationDto(Long id, String name, String issuer, LocalDate issuedDate) {
}
