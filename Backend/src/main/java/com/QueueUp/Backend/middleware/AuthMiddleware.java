package com.QueueUp.Backend.middleware;

import com.QueueUp.Backend.utils.JwtUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;

@Component
public class AuthMiddleware implements Filter {

    private final JwtUtils jwtUtils;

    public AuthMiddleware(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // 1. Allow public endpoints (Login/Signup) without checking token
        if (path.startsWith("/api/auth/") || path.equals("/api/health")) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Check for JWT Cookie
        String token = null;
        if (httpRequest.getCookies() != null) {
            token = Arrays.stream(httpRequest.getCookies())
                    .filter(c -> "jwt".equals(c.getName()))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElse(null);
        }

        // 3. Validate Token
        if (token != null) {
            try {
                Long userId = jwtUtils.validateTokenAndGetUserId(token);
                // Attach userId to request so Controllers can use it
                httpRequest.setAttribute("userId", userId);
                chain.doFilter(request, response); // Proceed to Controller
                return;
            } catch (Exception e) {
                // Token invalid
            }
        }

        // 4. Reject if no valid token
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.getWriter().write("{\"success\": false, \"message\": \"Not authorized\"}");
    }
}