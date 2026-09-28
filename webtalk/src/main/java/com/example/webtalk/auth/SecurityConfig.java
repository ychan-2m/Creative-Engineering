package com.example.webtalk.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 인증/인가 설정. 로그인/로그아웃은 AuthController에서 직접 처리하고,
 * 여기서는 어떤 요청이 로그인 없이 허용되는지(3.5, 5.8 보안 기준)와
 * 세션을 HttpSession에 저장하는 방식만 정의한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // OWASP Password Storage Cheat Sheet 권고에 따라 BCrypt 해시를 사용한다.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                              PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        // 로그인 성공 시 SecurityContext를 HttpSession에 저장해 REST와 WebSocket이 같은 세션을 공유한다.
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        http
                // 브라우저 세션 쿠키로 인증하는 JSON API이므로 폼 제출이 아닌 fetch 호출에는
                // CSRF 토큰을 함께 보낼 수 없다. 대신 SameSite 쿠키(WebConfig)로 교차 사이트 요청을 막는다.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/ws/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // H2 콘솔은 iframe을 사용한다.
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // "/"는 내부적으로 static/index.html에 대한 요청으로 다시 처리되므로
                                // "/index.html" 경로도 함께 허용해야 루트 접속이 막히지 않는다.
                                "/", "/index.html", "/login.html", "/signup.html", "/chat.html",
                                "/css/**", "/js/**",
                                "/api/auth/signup", "/api/auth/login",
                                "/h2-console/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable()) // 로그아웃은 AuthController#logout에서 JSON으로 응답한다.
                .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> {
                    // 부록 B 오류 응답 형식과 동일한 고정 본문이라 직렬화 라이브러리 의존 없이 직접 작성한다.
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"code\":\"UNAUTHENTICATED\",\"message\":\"로그인이 필요합니다.\"}");
                }));

        return http.build();
    }
}
