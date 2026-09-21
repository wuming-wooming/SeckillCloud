package org.sangoria.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@ConditionalOnClass(PasswordEncoder.class)
@EnableConfigurationProperties(PasswordEncoderProperties.class)
public class PasswordEncoderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder(PasswordEncoderProperties props) {

        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(props.getStrength()));
        encoders.put("pbkdf2", new Pbkdf2PasswordEncoder(
                props.getSecret() == null ? "" : props.getSecret(),
                16,
                props.getStrength() <= 0 ? 185_000 : props.getStrength(),
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256));

        String defaultAlgo = props.getAlgorithm() == null ? "bcrypt" : props.getAlgorithm();
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder(defaultAlgo, encoders);

        // 关键：兼容历史无前缀数据（裸 BCrypt），让老用户仍能登录
        delegating.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());

        return delegating;
    }
}