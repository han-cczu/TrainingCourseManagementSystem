package com.example.tcms.auth.dto;

import java.time.LocalDateTime;

import com.example.tcms.user.dto.UserVO;

public record LoginResponse(String token, LocalDateTime expiresAt, UserVO user) {
}
