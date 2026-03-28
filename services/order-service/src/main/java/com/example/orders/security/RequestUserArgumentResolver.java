package com.example.orders.security;

import com.example.orders.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class RequestUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(RequestUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String userIdHeader = request != null ? request.getHeader("X-User-Id") : null;
        String usernameHeader = request != null ? request.getHeader("X-Username") : null;
        String roleHeader = request != null ? request.getHeader("X-User-Role") : null;

        if (userIdHeader == null || usernameHeader == null || roleHeader == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Missing request user context");
        }

        return new RequestUser(Long.parseLong(userIdHeader), usernameHeader, roleHeader);
    }
}
