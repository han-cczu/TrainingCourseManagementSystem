package com.example.tcms.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateEnabledRequest(@NotNull(message = "enabled 不能为空") Boolean enabled) {
}
