package com.jukebox.live.filter;

import com.jukebox.live.entity.Party;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.security.JwtUtil;
import com.jukebox.live.util.NetworkUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class PartyPresenceFilter implements Filter {

    private final PartyRepository partyRepository;
    private final JwtUtil jwtUtil;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // Skip for auth, websocket, library, admin, and party creation endpoints
        // Admin endpoints don't require party context (auth handled by Spring Security)
        // Library endpoints are public (auth handled by Spring Security)
        // Party creation doesn't have a party ID yet
        // Guest list endpoint has partyId in URL path and is protected by @PreAuthorize
        if (path.startsWith("/api/auth/") || path.startsWith("/ws/") ||
            path.startsWith("/api/library/") || path.startsWith("/api/admin/") ||
            path.equals("/api/parties/create") || path.matches("(?i)/api/parties/[a-f0-9-]+/guests") ||
            path.equals("/health")) {
            chain.doFilter(request, response);
            return;
        }

        String partyIdHeader = httpRequest.getHeader("X-Party-ID");
        if ((partyIdHeader == null || partyIdHeader.isEmpty()) && path.equals("/api/playback/audio")) {
            partyIdHeader = httpRequest.getParameter("partyId");
        }
        if (partyIdHeader == null || partyIdHeader.isEmpty()) {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write("{\"message\":\"Missing X-Party-ID header\"}");
            return;
        }

        Party party;
        try {
            party = partyRepository.findById(UUID.fromString(partyIdHeader)).orElse(null);
        } catch (IllegalArgumentException e) {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write("{\"message\":\"Invalid party ID\"}");
            return;
        }

        if (party == null || !party.getIsActive()) {
            httpResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            httpResponse.getWriter().write("{\"message\":\"Party not found or has ended\"}");
            return;
        }

        // IP check - guest must be on the same local network subnet as the party host
        String clientIp = getClientIp(httpRequest);
        if (!NetworkUtil.isSameSubnet(clientIp, party.getPartyIp())) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.getWriter().write("{\"message\":\"You must be on the party WiFi network\",\"code\":\"NOT_ON_NETWORK\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        // Do not trust X-Forwarded-For header as it can be spoofed by clients
        // Use only the direct connection IP for local network enforcement
        return request.getRemoteAddr();
    }
}
