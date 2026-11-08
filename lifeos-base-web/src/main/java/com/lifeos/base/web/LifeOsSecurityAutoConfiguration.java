package com.lifeos.base.web;

import com.lifeos.base.web.error.ProblemWriter;
import com.lifeos.base.web.security.LifeOsSecurityProperties;
import com.lifeos.base.web.security.ProblemSecurityHandlers;
import com.lifeos.base.web.security.UserIdHeaderConsistencyFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The inner security layer: every service validates the JWT itself (as an OAuth2 resource server), so a
 * request that bypasses the gateway gets nothing. Stateless, no sessions, no CSRF (no cookies are used),
 * everything except {@link LifeOsSecurityProperties#publicPaths()} needs a valid token.
 *
 * <p>Runs before Spring Boot's own web-security auto-configurations so they back off. A service that needs a
 * different chain defines its own {@link SecurityFilterChain} bean and this one steps aside.
 */
@AutoConfiguration(
        after = LifeOsWebAutoConfiguration.class,
        before = {
                OAuth2ResourceServerWebSecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                ManagementWebSecurityAutoConfiguration.class
        })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LifeOsSecurityProperties.class)
public class LifeOsSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain lifeOsSecurityFilterChain(HttpSecurity http, LifeOsSecurityProperties properties,
                                                         ProblemWriter problemWriter) throws Exception {
        AuthenticationEntryPoint entryPoint = ProblemSecurityHandlers.entryPoint(problemWriter);
        AccessDeniedHandler accessDeniedHandler = ProblemSecurityHandlers.accessDeniedHandler(problemWriter);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(properties.publicPaths().toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(new UserIdHeaderConsistencyFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
