package com.onlinejudge.contracts;

import com.onlinejudge.common.security.AuthenticationRequiredException;
import com.onlinejudge.common.security.CurrentUser;
import com.onlinejudge.common.security.HeaderCurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #310 C-01/C-02 AUTH 认证上下文与网关鉴权信息传递契约：
 * 身份头解析必须成功、拒绝、稳定且缺省时失败关闭。
 */
class AuthContextContractTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void resolvesFrozenAuthContextFromGatewayHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "501");
        request.addHeader("X-Username", "teacher01");
        request.addHeader("X-User-Role", "teacher");
        request.addHeader("X-Permissions", "course:manage, grade:manage, course:manage");
        bind(request);

        HeaderCurrentUserProvider provider = new HeaderCurrentUserProvider();

        Optional<CurrentUser> currentUser = provider.getCurrentUser();
        assertThat(currentUser).isPresent();
        assertThat(currentUser.get().id()).isEqualTo(501L);
        assertThat(currentUser.get().role()).isEqualTo("TEACHER");
        assertThat(currentUser.get().hasRole("teacher")).isTrue();
        assertThat(currentUser.get().hasPermission("grade:manage")).isTrue();
        assertThat(currentUser.get().permissions()).containsExactly("course:manage", "grade:manage");
    }

    @Test
    void missingOrMalformedIdentityIsRejectedFailClosed() {
        HeaderCurrentUserProvider provider = new HeaderCurrentUserProvider();

        assertThat(provider.getCurrentUser()).isEmpty();

        MockHttpServletRequest missingRole = new MockHttpServletRequest();
        missingRole.addHeader("X-User-Id", "501");
        bind(missingRole);
        assertThat(provider.getCurrentUser()).isEmpty();

        MockHttpServletRequest malformedId = new MockHttpServletRequest();
        malformedId.addHeader("X-User-Id", "abc");
        malformedId.addHeader("X-User-Role", "TEACHER");
        bind(malformedId);
        assertThat(provider.getCurrentUser()).isEmpty();

        MockHttpServletRequest nonPositiveId = new MockHttpServletRequest();
        nonPositiveId.addHeader("X-User-Id", "0");
        nonPositiveId.addHeader("X-User-Role", "TEACHER");
        bind(nonPositiveId);
        assertThat(provider.getCurrentUser()).isEmpty();
    }

    @Test
    void requireCurrentUserFailsClosedWhenNoIdentityIsPresent() {
        HeaderCurrentUserProvider provider = new HeaderCurrentUserProvider();

        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void duplicateResolutionIsStableAndPermissionsAreUnmodifiable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "501");
        request.addHeader("X-User-Role", "ADMIN");
        request.addHeader("X-Permissions", "course:manage");
        bind(request);

        HeaderCurrentUserProvider provider = new HeaderCurrentUserProvider();

        CurrentUser first = provider.getCurrentUser().orElseThrow();
        CurrentUser second = provider.getCurrentUser().orElseThrow();
        assertThat(first).isEqualTo(second);
        assertThatThrownBy(() -> first.permissions().add("grade:manage"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
