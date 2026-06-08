package com.neuramesh.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 过滤器：每次请求若带合法 {@code Authorization: Bearer <accessToken>}，
 * 则解析为 {@link UserPrincipal} 并注入 Spring Security 上下文（角色映射为 {@code ROLE_<role>}）。
 *
 * <p>无令牌或令牌非法时不抛错（放行给后续授权规则决定，未认证端点照常公开）。
 */
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                Claims claims = jwtUtil.parse(token);
                // 仅 access 令牌可用于鉴权访问
                if (JwtUtil.TYPE_ACCESS.equals(claims.get(JwtUtil.CLAIM_TYPE, String.class))) {
                    UserPrincipal principal = jwtUtil.toPrincipal(claims);
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 令牌非法：保持未认证状态，交由授权规则处理
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
