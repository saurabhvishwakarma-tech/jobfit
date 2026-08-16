package com.jobfit.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitingFilterTest {

    @Test
    void allowsRequestsUpToTheLimitThenReturns429() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(3);
        FilterChain chain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = authRequest("/api/auth/login", "203.0.113.5");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200); // MockHttpServletResponse defaults to 200 when unset
        }
        verify(chain, times(3)).doFilter(Mockito.any(), Mockito.any());

        MockHttpServletRequest fourthRequest = authRequest("/api/auth/login", "203.0.113.5");
        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();
        filter.doFilterInternal(fourthRequest, fourthResponse, chain);

        assertThat(fourthResponse.getStatus()).isEqualTo(429);
        assertThat(fourthResponse.getHeader("Retry-After")).isNotNull();
        verify(chain, times(3)).doFilter(Mockito.any(), Mockito.any()); // still 3 - the 4th never reached the chain
    }

    @Test
    void nonAuthPathsAreNeverRateLimited() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(1);
        FilterChain chain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = authRequest("/api/jobs", "203.0.113.5");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
        }

        verify(chain, times(5)).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void differentAuthPathsAreTrackedIndependently() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(1);
        FilterChain chain = Mockito.mock(FilterChain.class);

        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        filter.doFilterInternal(authRequest("/api/auth/login", "203.0.113.5"), loginResponse, chain);
        assertThat(loginResponse.getStatus()).isEqualTo(200);

        // A second call to /login from the same IP should now be blocked...
        MockHttpServletResponse secondLoginResponse = new MockHttpServletResponse();
        filter.doFilterInternal(authRequest("/api/auth/login", "203.0.113.5"), secondLoginResponse, chain);
        assertThat(secondLoginResponse.getStatus()).isEqualTo(429);

        // ...but /register from the same IP is a separate bucket and still allowed.
        MockHttpServletResponse registerResponse = new MockHttpServletResponse();
        filter.doFilterInternal(authRequest("/api/auth/register", "203.0.113.5"), registerResponse, chain);
        assertThat(registerResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void differentIpsAreTrackedIndependently() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(1);
        FilterChain chain = Mockito.mock(FilterChain.class);

        MockHttpServletResponse firstIpResponse = new MockHttpServletResponse();
        filter.doFilterInternal(authRequest("/api/auth/login", "203.0.113.5"), firstIpResponse, chain);
        assertThat(firstIpResponse.getStatus()).isEqualTo(200);

        MockHttpServletResponse secondIpResponse = new MockHttpServletResponse();
        filter.doFilterInternal(authRequest("/api/auth/login", "198.51.100.7"), secondIpResponse, chain);
        assertThat(secondIpResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void zeroOrNegativeLimitDisablesRateLimitingEntirely() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(0);
        FilterChain chain = Mockito.mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(authRequest("/api/auth/login", "203.0.113.5"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(chain, times(10)).doFilter(Mockito.any(), Mockito.any());
    }

    private static MockHttpServletRequest authRequest(String uri, String remoteIp) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(remoteIp);
        return request;
    }
}
