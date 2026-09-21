package org.sangoria.common.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.sangoria.common.constant.AuthConstants;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class JwtUtil {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    """
                            seckill.jwt.secret 未配置。请检查：
                            1. Nacos 中 seckill-common.yml 是否存在于当前 namespace/group；
                            2. spring.cloud.nacos.config.namespace 是否使用了正确的 namespace ID；
                            3. spring.config.import 是否使用了 optional: 前缀吞掉了加载失败。""");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("seckill.jwt.secret 长度必须 ≥ 32 字节，当前 " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String createAccessToken(Long userId, String username, List<String> roles) {
        return build(userId, username, roles,
                AuthConstants.TOKEN_TYPE_ACCESS, props.getAccessTtl());
    }

    public String createRefreshToken(Long userId) {
        return build(userId, null, null,
                AuthConstants.TOKEN_TYPE_REFRESH, props.getRefreshTtl());
    }

    private String build(Long userId, String username, List<String> roles,
                         String type, Duration ttl) {
        Instant now = Instant.now();
        JwtBuilder b = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(props.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(AuthConstants.CLAIM_TYPE, type)
                .signWith(key);
        if (username != null) b.claim(AuthConstants.CLAIM_USERNAME, username);
        if (roles != null) b.claim(AuthConstants.CLAIM_ROLES, roles);
        return b.compact();
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}