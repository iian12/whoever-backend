package com.jygoh.whoever.domain.follow;

import com.jygoh.whoever.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerAndFollowee(Member follower, Member followee);


}
