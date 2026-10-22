package com.pulsepass.backofficegateway.unit.api.configurations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pulsepass.backofficegateway.api.configurations.CsrfCookieFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

class CsrfCookieFilterTest {

    @Test
    void doFilter_requestWithCsrfToken_loadsTokenAndContinuesChain() throws Exception {
        CsrfToken csrfToken = mock(CsrfToken.class);
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(CsrfToken.class.getName(), csrfToken);

        new CsrfCookieFilter().doFilter(request, response, filterChain);

        verify(csrfToken).getToken();
        verify(filterChain).doFilter(request, response);
    }
}
