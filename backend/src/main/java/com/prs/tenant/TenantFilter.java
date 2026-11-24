package com.prs.tenant;

import com.prs.entity.Organization;
import com.prs.entity.OrganizationMembership;
import com.prs.entity.Role;
import com.prs.entity.User;
import com.prs.repository.OrganizationMembershipRepository;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            if (path.startsWith("/api/auth") || path.startsWith("/api/health") || path.startsWith("/h2-console") || path.equals("/error")) {
                filterChain.doFilter(request, response);
                return;
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                String username = auth.getName();
                Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(username);

                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    String orgHeader = request.getHeader("X-Organization-Id");
                    if (orgHeader == null || orgHeader.isBlank()) {
                        orgHeader = request.getParameter("organizationId");
                    }

                    if (orgHeader != null && !orgHeader.isBlank()) {
                        try {
                            Long orgId = Long.parseLong(orgHeader.trim());
                            Organization org = organizationRepository.findById(orgId).orElse(null);
                            if (org == null) {
                                if (path.startsWith("/api/organizations")) {
                                    // Stale header on org listing/management; ignore and continue
                                } else {
                                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Organization not found.");
                                    return;
                                }
                            } else {
                                boolean isGlobalAdmin = user.getRole() == Role.ADMIN;
                                Optional<OrganizationMembership> memOpt = membershipRepository.findByUserIdAndOrganizationId(user.getId(), orgId);

                                if (!isGlobalAdmin && memOpt.isEmpty()) {
                                    if (path.startsWith("/api/organizations")) {
                                        // Stale header on org discovery; ignore header and let controller process user-scoped request
                                    } else {
                                        sendForbidden(response, "Access denied: You do not have membership in organization '" + org.getName() + "'.");
                                        return;
                                    }
                                } else {
                                    if (memOpt.isPresent()) {
                                        OrganizationMembership membership = memOpt.get();
                                        if ("SUSPENDED".equalsIgnoreCase(membership.getStatus())) {
                                            sendForbidden(response, "Access denied: Your membership in organization '" + org.getName() + "' is suspended.");
                                            return;
                                        }
                                        TenantContext.setMembership(membership);
                                    }

                                    // Suspended organization check
                                    if ("SUSPENDED".equalsIgnoreCase(org.getStatus()) && !path.startsWith("/api/organizations")) {
                                        sendForbidden(response, "Organization '" + org.getName() + "' is suspended. Ordinary operations are disabled.");
                                        return;
                                    }

                                    TenantContext.setOrganization(org);
                                }
                            }
                        } catch (NumberFormatException nfe) {
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Organization-Id header format.");
                            return;
                        }
                    } else {
                        // Resolve default active organization for user
                        List<OrganizationMembership> memberships = membershipRepository.findByUserId(user.getId());
                        Optional<OrganizationMembership> activeMem = memberships.stream()
                                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                                .findFirst();

                        if (activeMem.isPresent()) {
                            OrganizationMembership mem = activeMem.get();
                            Organization org = mem.getOrganization();
                            if ("SUSPENDED".equalsIgnoreCase(org.getStatus()) && !path.startsWith("/api/organizations")) {
                                sendForbidden(response, "Organization '" + org.getName() + "' is suspended. Ordinary operations are disabled.");
                                return;
                            }
                            TenantContext.setOrganization(org);
                            TenantContext.setMembership(mem);
                        } else if (user.getRole() == Role.ADMIN) {
                            // Global admin without explicit memberships gets first organization if available
                            List<Organization> allOrgs = organizationRepository.findAll();
                            if (!allOrgs.isEmpty()) {
                                TenantContext.setOrganization(allOrgs.get(0));
                            }
                        }
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"%s\"}", message));
    }
}
