package org.sangoria.seckillgateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.sangoria.common.constant.AuthConstants;
import org.sangoria.common.jwt.JwtUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private static final Set<String> WHITE_LIST = Set.of(
            "/api/user/login",
            "/api/user/register",
            "/api/user/refresh",
            "/actuator"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getRequestURI();
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String token = req.getHeader(AuthConstants.HEADER_AUTHORIZATION);
        if (token == null || !token.startsWith(AuthConstants.BEARER_PREFIX)) {
            writeUnauthorized(resp);
            return;
        }
        token = token.substring(AuthConstants.BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (JwtException e) {
            writeUnauthorized(resp);
            return;
        }
        if (!AuthConstants.TOKEN_TYPE_ACCESS.equals(claims.get(AuthConstants.CLAIM_TYPE))) {
            writeUnauthorized(resp);
            return;
        }

        MutableHttpServletRequest mutable = new MutableHttpServletRequest(req);
        // 覆盖客户端可能伪造的同名 Header
        mutable.putHeader(AuthConstants.HEADER_USER_ID, claims.getSubject());
        Object uname = claims.get(AuthConstants.CLAIM_USERNAME);
        if (uname != null) mutable.putHeader(AuthConstants.HEADER_USERNAME, uname.toString());
        Object roles = claims.get(AuthConstants.CLAIM_ROLES);
        if (roles != null) mutable.putHeader(AuthConstants.HEADER_USER_ROLES, roles.toString());

        chain.doFilter(mutable, resp);
    }

    private void writeUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"code\":401,\"msg\":\"认证失败\",\"data\":null}");
    }
}