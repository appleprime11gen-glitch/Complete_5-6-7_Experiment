package com.example.postapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.Ordered;

import com.example.postapi.filter.CorrelationIdFilter;
import com.example.postapi.filter.LoggingFilter;

@SpringBootApplication
@EnableCaching
public class PostapiApplication {

	@Bean
	FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
		FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new CorrelationIdFilter());
		registration.addUrlPatterns("/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

	@Bean
	FilterRegistrationBean<LoggingFilter> loggingFilter() {
		FilterRegistrationBean<LoggingFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new LoggingFilter());
		registration.addUrlPatterns("/*");
		registration.setOrder(1);
		return registration;
	}

	public static void main(String[] args) {
		SpringApplication.run(PostapiApplication.class, args);
	}

}
