package org.sangoria.common.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "seckill.jwt")
public class JwtProperties {
    /** HS256 secret，至少 32 字节 */
    private String secret;
    private Duration accessTtl = Duration.ofMinutes(15);
    private Duration refreshTtl = Duration.ofHours(12);
    private String issuer = "seckill-cloud";
}