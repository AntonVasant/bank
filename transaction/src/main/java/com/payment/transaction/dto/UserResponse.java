package com.payment.transaction.dto;

import java.time.LocalDateTime;

public record UserResponse(Long id, String email, String fullName, String status, LocalDateTime createdAt) {}
