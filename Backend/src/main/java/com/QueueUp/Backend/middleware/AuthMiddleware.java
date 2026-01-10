package com.QueueUp.Backend.middleware;

import com.QueueUp.Backend.utils.JwtUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;

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

        // Allow all static resources and frontend routes to pass through.
        if (!path.startsWith("/api")) {
            chain.doFilter(request, response);
            return;
        }

        // public paths that do NOT need a token
        boolean isPublicAuthRoute = path.equals("/api/auth/login") || path.equals("/api/auth/signup") || path.startsWith("/api/auth/spotify/");

        if (isPublicAuthRoute || path.equals("/api/health")) {
            chain.doFilter(request, response);
            return;
        }

        //  Check for JWT Cookie
        String token = null;
        if (httpRequest.getCookies() != null) {
            token = Arrays.stream(httpRequest.getCookies())
                    .filter(c -> "jwt".equals(c.getName()))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElse(null);
        }

        //  Validate Token
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

        // Reject if no valid token
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.getWriter().write("{\"success\": false, \"message\": \"Not authorized\"}");
    }
}