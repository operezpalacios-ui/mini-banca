package com.minibanca.core.api.dto;

import java.time.Instant;

public record RefreshTokenResponse(String refreshToken, Instant expiraEn) {
}
