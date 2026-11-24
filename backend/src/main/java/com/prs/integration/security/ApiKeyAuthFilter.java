package com.prs.integration.security;

import com.prs.entity.ApiKey;
import com.prs.integration.apikey.ApiKeyService;
import com.prs.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawKey = request.getHeader("X-API-Key");
        if (rawKey == null || rawKey.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer prs_live_")) {
                rawKey = authHeader.substring(7);
            }
        }

        if (rawKey != null && rawKey.startsWith("prs_live_") && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<ApiKey> apiKeyOpt = apiKeyService.authenticateKey(rawKey.trim());
            if (apiKeyOpt.isPresent()) {
                ApiKey apiKey = apiKeyOpt.get();

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_API_CLIENT"));
                authorities.add(new SimpleGrantedAuthority("ROLE_ORGANIZATION_ADMIN")); // Scoped API client has admin rights within granted scopes

                if (apiKey.getScopes() != null) {
                    Arrays.stream(apiKey.getScopes().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
                }

                String principalName = "api-client-" + apiKey.getKeyPrefix();
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principalName, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Set tenant context for scoped API client
                if (apiKey.getOrganization() != null) {
                    TenantContext.setOrganization(apiKey.getOrganization());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
