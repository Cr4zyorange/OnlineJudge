package com.onlinejudge.common.security;

import com.onlinejudge.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String userId = request == null ? null : request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String roleValue = request.getHeader("X-User-Role");
        PlatformRole role = roleValue == null || roleValue.isBlank()
                ? PlatformRole.STUDENT
                : PlatformRole.valueOf(roleValue.trim().toUpperCase());
        String name = request.getHeader("X-User-Name");
        return new CurrentUser(Long.valueOf(userId), name == null || name.isBlank() ? "用户" + userId : name, role);
    }
}
