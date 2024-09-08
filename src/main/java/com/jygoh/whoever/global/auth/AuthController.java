package com.jygoh.whoever.global.auth;

import com.jygoh.whoever.domain.member.dto.MemberLoginRequestDto;
import com.jygoh.whoever.domain.member.otp.service.OtpVerifyRequestDto;
import com.jygoh.whoever.domain.member.otp.service.PasswordResetRequestDto;
import com.jygoh.whoever.domain.member.otp.service.PasswordResetService;
import com.jygoh.whoever.domain.member.otp.service.SendOtpRequestDto;
import com.jygoh.whoever.domain.member.service.MemberService;
import com.jygoh.whoever.global.security.jwt.JwtTokenProvider;
import com.jygoh.whoever.global.security.jwt.TokenResponseDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService,
        MemberService memberService, JwtTokenProvider jwtTokenProvider,
        CustomUserDetailsService customUserDetailsService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.memberService = memberService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@RequestBody MemberLoginRequestDto requestDto,
        HttpServletResponse response) {
        try {
            TokenResponseDto tokenResponse = authService.login(requestDto);

            // 리프레시 토큰을 쿠키로 설정
            Cookie refreshTokenCookie = new Cookie("refreshToken", tokenResponse.getRefreshToken());
            refreshTokenCookie.setHttpOnly(false);
            refreshTokenCookie.setSecure(false); // HTTPS 환경에서만 사용 가능
            refreshTokenCookie.setPath("/"); // 전체 경로에서 사용 가능
            refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60); // 7일

            response.addCookie(refreshTokenCookie);

            // 액세스 토큰을 JSON 응답으로 반환
            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            // 로그인 실패 시 401 상태 코드 반환
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }


    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@RequestBody SendOtpRequestDto requestDto) {
        try {
            passwordResetService.sendOtp(requestDto);
            return ResponseEntity.ok("OTP sent");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody OtpVerifyRequestDto requestDto) {
        try {
            boolean isValid = passwordResetService.verifyOtp(requestDto);
            return isValid ? ResponseEntity.ok("OTP is valid.")
                : ResponseEntity.badRequest().body("Invalid OTP.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody PasswordResetRequestDto requestDto) {
        try {
            passwordResetService.resetPassword(requestDto);
            return ResponseEntity.ok("Password has been reset.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshAccessToken(HttpServletRequest request) {
        String refreshToken = getRefreshTokenFromCookie(request);

        if (refreshToken != null || !jwtTokenProvider.validateToken(refreshToken)) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }

        Long memberId = jwtTokenProvider.getMemberIdFromToken(refreshToken);
        UserDetails userDetails = customUserDetailsService.loadUserById(memberId);

        String newAccessToken = jwtTokenProvider.createAccessToken(memberId);
        TokenResponseDto tokenResponseDto = new TokenResponseDto(newAccessToken, null);

        return ResponseEntity.ok(tokenResponseDto);
    }

    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("refreshToken")) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
