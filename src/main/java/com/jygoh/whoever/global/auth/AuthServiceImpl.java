package com.jygoh.whoever.global.auth;

import com.jygoh.whoever.domain.member.dto.MemberLoginRequestDto;
import com.jygoh.whoever.domain.member.entity.Member;
import com.jygoh.whoever.domain.member.repository.MemberRepository;
import com.jygoh.whoever.global.security.jwt.JwtTokenProvider;
import com.jygoh.whoever.global.security.jwt.RefreshToken;
import com.jygoh.whoever.global.security.jwt.RefreshTokenRepository;
import com.jygoh.whoever.global.security.jwt.TokenResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;

    public AuthServiceImpl(JwtTokenProvider jwtTokenProvider,
        RefreshTokenRepository refreshTokenRepository, MemberRepository memberRepository,
        BCryptPasswordEncoder passwordEncoder, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public TokenResponseDto login(MemberLoginRequestDto requestDto) {
        // 사용자 존재 여부 확인
        Member member = memberRepository.findByEmail(requestDto.getEmail())
            .orElseThrow(() -> new BadCredentialsException("User does not exist"));

        // 사용자 정보 로드
        UserDetails userDetails = userDetailsService.loadUserByUsername(requestDto.getEmail());
        // 비밀번호 검증
        if (passwordEncoder.matches(requestDto.getPassword(), userDetails.getPassword())) {
            String accessToken = jwtTokenProvider.createAccessToken(member.getId());
            String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

            // TokenResponseDto 반환
            return TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
        } else {
            throw new BadCredentialsException("Invalid credentials");
        }
    }


    @Override
    public TokenResponseDto refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        Long memberId = jwtTokenProvider.getMemberIdFromToken(refreshToken);
        RefreshToken existingRefreshToken = refreshTokenRepository.findByMemberId(memberId)
            .orElseThrow(
                () -> new IllegalArgumentException("Refresh token does not exist or is invalid"));
        if (!existingRefreshToken.getToken().equals(refreshToken)) {
            throw new IllegalArgumentException("Refresh token does not match");
        }
        String newAccessToken = jwtTokenProvider.createAccessToken(memberId);
        // TokenResponseDto 객체 생성
        return TokenResponseDto.builder().accessToken(newAccessToken).build();
    }
}
