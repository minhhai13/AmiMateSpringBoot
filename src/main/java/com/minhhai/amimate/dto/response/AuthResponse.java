package com.minhhai.amimate.dto.response;
public record AuthResponse(String token, long expiresInSeconds) {
}