package org.sangoria.seckillgateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthFilter implements Filter {

    private static final String[] WHITE_LIST = {
            "/api/user/login", "/api/user/register"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI();

        // 白名单放行
        for (String white : WHITE_LIST) {
            if (path.startsWith(white)) {
                chain.doFilter(request, response);
                return;
            }
        }

        String token = req.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"code\":401,\"msg\":\"未登录\"}");
            return;
        }

        // TODO: 调用 JWT 工具类校验 token
        chain.doFilter(request, response);
    }
}