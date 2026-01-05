package com.booknara.booknaraPrj.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/test/**",

                                //  도서 검색 화면/데이터
                                "/book/search",
                                "/book/search/list",

                                //  장르/카테고리
                                "/book/genres/**",

                                //  도서 상세 함
                                "/book/detail",

                                // 정적 리소스
                                "/css/**", "/js/**", "/images/**", "/favicon.ico",

                                "/ebook",
                                "/ebook/*",
                                "/ebook/epub/*",
                                "/ebook/history/*",
                                "/ebook/dict/*"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(Customizer.withDefaults());

        return http.build();
    }
}
