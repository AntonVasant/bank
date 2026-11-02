package com.payment.transaction.dto;

import com.payment.transaction.entity.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String email, String fullName, String status, LocalDateTime createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getStatus().name(), user.getCreatedAt());
    }
}
