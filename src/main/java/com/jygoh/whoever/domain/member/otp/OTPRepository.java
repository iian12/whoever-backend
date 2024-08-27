package com.jygoh.whoever.domain.member.otp;

import com.jygoh.whoever.domain.member.entity.Member;
import com.jygoh.whoever.domain.member.otp.model.OTP;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OTPRepository extends JpaRepository<OTP, Long> {

    Optional<OTP> findByMemberAndOtp(Member member, String otp);

    @Modifying
    @Query("DELETE FROM OTP o WHERE o.expiryTime < CURRENT_TIMESTAMP")
    void deleteExpiredOtps();
}
