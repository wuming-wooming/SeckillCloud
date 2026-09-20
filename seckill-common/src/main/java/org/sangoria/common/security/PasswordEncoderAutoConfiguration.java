package org.sangoria.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@AutoConfiguration
@ConditionalOnClass(PasswordEncoder.class)
@EnableConfigurationProperties(PasswordEncoderProperties.class)
public class PasswordEncoderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder(PasswordEncoderProperties props) {
        String algo = props.getAlgorithm() == null
                ? "bcrypt" : props.getAlgorithm().toLowerCase();
        return switch (algo) {
            case "bcrypt" -> new BCryptPasswordEncoder(props.getStrength());
            case "pbkdf2" -> new Pbkdf2PasswordEncoder(
                    props.getSecret() == null ? "" : props.getSecret(),
                    16,
                    props.getStrength() <= 0 ? 185_000 : props.getStrength(),
                    Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
            default -> throw new IllegalArgumentException(
                    "不支持的密码加密算法: " + props.getAlgorithm());
        };
    }
}