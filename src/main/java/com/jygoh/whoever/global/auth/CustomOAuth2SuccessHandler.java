package com.jygoh.whoever.global.auth;

import com.jygoh.whoever.domain.member.entity.Member;
import com.jygoh.whoever.domain.member.repository.MemberRepository;
import com.jygoh.whoever.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class CustomOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;

    public CustomOAuth2SuccessHandler(JwtTokenProvider jwtTokenProvider,
        MemberRepository memberRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.memberRepository = memberRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException {
        CustomUserDetail userDetail = (CustomUserDetail) authentication.getPrincipal();

        Long memberId = userDetail.getMemberId();
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("User not Found"));

        if (!member.isSignUp()) {
            response.sendRedirect("http://localhost:3000/signup/nickname");
        } else {
            String accessToken = jwtTokenProvider.createAccessToken(userDetail.getMemberId());
            String refreshToken = jwtTokenProvider.createRefreshToken(userDetail.getMemberId());
            Cookie accessTokenCookie = createCookie("accessToken", accessToken);
            Cookie refreshTokenCookie = createCookie("refreshToken", refreshToken);
            // 쿠키를 응답에 추가
            response.addCookie(accessTokenCookie);
            response.addCookie(refreshTokenCookie);
            // 클라이언트를 리다이렉트
            response.sendRedirect("http://localhost:3000/login/callback");
        }
    }

    private Cookie createCookie(String name, String token) {
        if (name.equals("accessToken")) {
            Cookie cookie = new Cookie(name, token);
            cookie.setHttpOnly(false);
            cookie.setSecure(false);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 2);
            return cookie;
        } else {
            Cookie cookie = new Cookie(name, token);
            cookie.setHttpOnly(false);
            cookie.setSecure(false);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 24 * 14);
            return cookie;
        }
    }
}
