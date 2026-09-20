package org.sangoria.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "seckill.security.password")
public class PasswordEncoderProperties {

    /** 支持 bcrypt / pbkdf2，默认 bcrypt */
    private String algorithm = "bcrypt";

    /**
     * bcrypt 语义：strength（cost，4~31，默认 10）
     * pbkdf2 语义：迭代次数（默认 185000）
     */
    private int strength = 10;

    /** 仅 pbkdf2 用；为空时表示不加 pepper。bcrypt 会忽略该字段 */
    private String secret;
}