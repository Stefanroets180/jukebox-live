package com.jukebox.live.filter;

import com.jukebox.live.entity.Party;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyPresenceFilterAudioRequestTest {

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private JwtUtil jwtUtil;

    private PartyPresenceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PartyPresenceFilter(partyRepository, jwtUtil);
    }

    @Test
    void audioElementCanUsePartyQueryWithoutCustomHeaderAndStillMustBeOnLan() throws IOException, ServletException {
        UUID partyId = UUID.randomUUID();
        Party party = Party.builder()
                .id(partyId)
                .partyIp("192.168.1.100")
                .isActive(true)
                .build();
        when(partyRepository.findById(partyId)).thenReturn(Optional.of(party));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/playback/audio");
        request.setParameter("partyId", partyId.toString());
        request.setRemoteAddr("192.168.1.45");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        verify(partyRepository).findById(partyId);
    }

    @Test
    void audioQueryFallbackDoesNotBypassPartyLanRestriction() throws IOException, ServletException {
        UUID partyId = UUID.randomUUID();
        Party party = Party.builder()
                .id(partyId)
                .partyIp("192.168.1.100")
                .isActive(true)
                .build();
        when(partyRepository.findById(partyId)).thenReturn(Optional.of(party));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/playback/audio");
        request.setParameter("partyId", partyId.toString());
        request.setRemoteAddr("10.0.0.45");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new AssertionError("Requests from outside the party subnet must not reach the controller.");
        };

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(partyRepository).findById(partyId);
    }
}
