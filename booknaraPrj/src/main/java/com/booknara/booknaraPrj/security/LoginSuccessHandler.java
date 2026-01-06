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

        if (principal instanceof CustomOAuth2User oAuth2User) {
            user = oAuth2User.getUser();
        } else if (principal instanceof CustomUserDetails userDetails) {
            user = userDetails.getUser();
        } else {
            response.sendRedirect("/home");
            return;
        }

        if (!"Y".equals(user.getExtraInfoDone())) {
            response.sendRedirect("/users/signup-extra");
        } else {
            response.sendRedirect("/home");
        }
    }
}

