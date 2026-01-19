package com.booknara.booknaraPrj.security;

import com.booknara.booknaraPrj.login_signup.User;
import com.booknara.booknaraPrj.security.oauth.CustomOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        Object principal = authentication.getPrincipal();
        User user;

        // 1. 유저 정보 추출
        if (principal instanceof CustomOAuth2User oAuth2User) {
            user = oAuth2User.getUser();
        } else if (principal instanceof CustomUserDetails userDetails) {
            user = userDetails.getUser();
        } else {
            // 예외 상황 시 기본 홈으로 리디렉션
            getRedirectStrategy().sendRedirect(request, response, "/home");
            return;
        }

        // 2. 리디렉션 타겟 결정 (우선순위 로직)
        String targetUrl = determineTargetUrl(request, user);

        // 3. ✅ 핵심 수정: 스프링 시큐리티의 RedirectStrategy 사용
        // 이 메서드가 'forward-headers-strategy' 설정을 참조하여
        // http -> https 프로토콜 불일치 문제를 자동으로 해결합니다.
        clearAuthenticationAttributes(request); // 세션에 저장된 에러 속성 등 정리
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * 로직에 따른 최종 목적지 주소를 결정합니다.
     */
    private String determineTargetUrl(HttpServletRequest request, User user) {
        // 1) 추가정보 미완료 시 무조건 추가 정보 입력 페이지로
        if (!"Y".equals(user.getExtraInfoDone())) {
            return "/users/signup-extra";
        }

        // 2) redirect 파라미터가 있는 경우 (이전 페이지 복귀용)
        String redirectParam = request.getParameter("redirect");
        if (redirectParam != null && !redirectParam.isBlank()) {
            // 오픈 리다이렉트 방지: 내부 경로(/)로 시작하는 경우만 허용
            if (redirectParam.startsWith("/") && !redirectParam.startsWith("//")) {
                return redirectParam;
            }
        }

        // 3) 기본 목적지
        return "/home";
    }
}