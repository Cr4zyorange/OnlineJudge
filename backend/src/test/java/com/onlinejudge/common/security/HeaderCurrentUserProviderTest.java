package com.onlinejudge.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderCurrentUserProviderTest {
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void resolvesCurrentUserFromDocumentedAuthHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "501");
        request.addHeader("X-Username", "teacher01");
        request.addHeader("X-User-Role", "TEACHER");
        request.addHeader("X-Permissions", "course:manage,grade:manage");
        bind(request);

        HeaderCurrentUserProvider provider = new HeaderCurrentUserProvider();

        Optional<CurrentUser> currentUser = provider.getCurrentUser();

        assertThat(currentUser).isPresent();
        assertThat(currentUser.get().id()).isEqualTo(501L);
        assertThat(currentUser.get().username()).isEqualTo("teacher01");
        assertThat(currentUser.get().role()).isEqualTo("TEACHER");
        assertThat(currentUser.get().hasRole("teacher")).isTrue();
        assertThat(currentUser.get().hasPermission("grade:manage")).isTrue();
    }

    @Test
    void returnsEmptyWhenAuthHeadersAreMissingOrInvalid() {
        HeaderCurrentUserProvider provider = new HeaderCurrentUserProvider();

        assertThat(provider.getCurrentUser()).isEmpty();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "not-a-number");
        request.addHeader("X-User-Role", "TEACHER");
        bind(request);

        assertThat(provider.getCurrentUser()).isEmpty();
    }

    @Test
    void requireCurrentUserFailsWithAuthErrorWhenNoLoginContextExists() {
        HeaderCurrentUserProvider provider = new HeaderCurrentUserProvider();

        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOf(AuthenticationRequiredException.class)
                .hasMessageContaining("未登录或登录状态已失效");
    }

    private void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
