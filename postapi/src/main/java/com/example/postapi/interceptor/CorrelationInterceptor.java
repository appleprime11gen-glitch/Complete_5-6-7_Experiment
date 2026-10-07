package com.example.postapi.interceptor;

import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.example.postapi.filter.CorrelationIdFilter;

public class CorrelationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            response.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        }
        return true;
    }


}
