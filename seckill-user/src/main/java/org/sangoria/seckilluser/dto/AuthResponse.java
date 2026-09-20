package org.sangoria.seckilluser.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
}
