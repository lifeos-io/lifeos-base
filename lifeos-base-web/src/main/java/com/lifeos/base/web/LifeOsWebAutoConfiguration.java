package com.lifeos.base.web;

import com.lifeos.base.web.correlation.CorrelationIdFilter;
import com.lifeos.base.web.error.LifeOsExceptionHandler;
import com.lifeos.base.web.error.OptimisticLockExceptionHandler;
import com.lifeos.base.web.error.ProblemWriter;
import com.lifeos.base.web.error.UnexpectedExceptionHandler;
import com.lifeos.base.web.security.CurrentUserArgumentResolver;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servlet-side defaults every Life OS service gets by depending on {@code lifeos-base-web}:
 * correlation-ID filter, RFC 9457 problem responses, {@code @CurrentUser} and a UTC {@link Clock}.
 * Every bean backs off when the service defines its own.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LifeOsWebAutoConfiguration {

    /** Runs before Spring Security's filter chain (order -100) so 401/403 responses carry the ID too. */
    @Bean
    @ConditionalOnMissingBean(name = "lifeOsCorrelationIdFilter")
    public FilterRegistrationBean<CorrelationIdFilter> lifeOsCorrelationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemWriter problemWriter(JsonMapper jsonMapper) {
        return new ProblemWriter(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LifeOsExceptionHandler lifeOsExceptionHandler() {
        return new LifeOsExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public UnexpectedExceptionHandler lifeOsUnexpectedExceptionHandler() {
        return new UnexpectedExceptionHandler();
    }

    @Bean
    public WebMvcConfigurer lifeOsCurrentUserConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }

    /** Only when spring-tx is present (services bring it with Spring Data JPA). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.dao.OptimisticLockingFailureException")
    static class OptimisticLockConfiguration {

        @Bean
        @ConditionalOnMissingBean
        OptimisticLockExceptionHandler lifeOsOptimisticLockExceptionHandler() {
            return new OptimisticLockExceptionHandler();
        }
    }
}
