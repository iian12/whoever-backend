package com.jygoh.whoever.domain.post.service;

import com.jygoh.whoever.domain.hashtag.model.Hashtag;
import com.jygoh.whoever.domain.hashtag.service.HashtagService;
import com.jygoh.whoever.domain.member.entity.Member;
import com.jygoh.whoever.domain.member.repository.MemberRepository;
import com.jygoh.whoever.domain.post.dto.PostCreateRequestDto;
import com.jygoh.whoever.domain.post.dto.PostDetailResponseDto;
import com.jygoh.whoever.domain.post.dto.PostListResponseDto;
import com.jygoh.whoever.domain.post.dto.PostUpdateRequestDto;
import com.jygoh.whoever.domain.post.like.PostLike;
import com.jygoh.whoever.domain.post.like.PostLikeRepository;
import com.jygoh.whoever.domain.post.model.Post;
import com.jygoh.whoever.domain.post.repository.PostRepository;
import com.jygoh.whoever.domain.post.view.model.View;
import com.jygoh.whoever.domain.post.view.repository.ViewRepository;
import com.jygoh.whoever.global.auth.CustomUserDetails;
import com.jygoh.whoever.global.auth.CustomUserDetailsService;
import com.jygoh.whoever.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final HashtagService hashtagService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final CustomUserDetailsService customUserDetailsService;
    private final MemberRepository memberRepository;
    private final ViewRepository viewRepository;
    private final PostLikeRepository postLikeRepository;

    private final static long VIEW_EXPIRATION_TIME = 60 * 5;

    public PostServiceImpl(PostRepository postRepository,
         HashtagService hashtagService, RedisTemplate<String, String> redisTemplate,JwtTokenProvider jwtTokenProvider,
        CustomUserDetailsService customUserDetailsService, MemberRepository memberRepository,
        ViewRepository viewRepository, PostLikeRepository postLikeRepository) {
        this.postRepository = postRepository;
        this.hashtagService = hashtagService;
        this.redisTemplate = redisTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
        this.customUserDetailsService = customUserDetailsService;
        this.memberRepository = memberRepository;
        this.viewRepository = viewRepository;
        this.postLikeRepository = postLikeRepository;
    }

    @Override
    public Long createPost(PostCreateRequestDto requestDto, String token) {

       Long memberId = jwtTokenProvider.getMemberIdFromToken(token);

       UserDetails userDetails = customUserDetailsService.loadUserById(memberId);

       Member author = ((CustomUserDetails) userDetails).getMember();

       List<Hashtag> hashtags = hashtagService.findOrCreateHashtags(requestDto.getHashtagNames());

       Post post = requestDto.toEntity(author, author.getNickname(), hashtags);

       postRepository.save(post);

       return post.getId();
    }

    @Override
    public void updatePost(Long postId, PostUpdateRequestDto requestDto) {

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        List<Hashtag> hashtags = hashtagService.findOrCreateHashtags(requestDto.getHashtagNames());

        // Post 엔티티의 도메인 메서드를 사용하여 업데이트
        post.updatePost(requestDto.getTitle(), requestDto.getContent(), hashtags);

        postRepository.save(post);
    }

    @Override
    public void deletePost(Long postId) {

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        postRepository.delete(post);
    }

    @Override
    public List<PostListResponseDto> getAllPosts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
        Page<Post> postPage = postRepository.findAll(pageable);

        return postPage.stream()
                .map(PostListResponseDto::new)
                .collect(Collectors.toList());
    }

    @Override
    public PostDetailResponseDto getPostDetail(Long postId, String token) {
        String redisKey = "postView:" + postId;

        String userId = null;
        if (token != null && !token.isEmpty()) {
            try {
                Long memberId = jwtTokenProvider.getMemberIdFromToken(token);
                if (memberId != null) {
                    Member member = memberRepository.findById(memberId)
                        .orElseThrow(() -> new IllegalArgumentException("Member not found"));
                    userId = memberId.toString(); // Redis에 사용할 userId
                }
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Error processing token: " + e.getMessage(), e);
            } catch (Exception e) {
                throw new RuntimeException("An unexpected error occurred: " + e.getMessage(), e);
            }
        }

        // Redis에서 조회 여부 확인
        if (userId != null) {
            redisKey += ":" + userId;
        }
        Boolean hasViewed = redisTemplate.hasKey(redisKey);

        // 조회한 적이 없다면 조회수를 증가시키고 Redis에 키를 추가
        if (hasViewed == null || !hasViewed) {
            Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

            post.incrementViewCount();  // 조회수 증가
            postRepository.save(post);

            // Redis에 키를 추가하고 일정 시간 후에 자동으로 만료되도록 설정
            redisTemplate.opsForValue().set(redisKey, "true", VIEW_EXPIRATION_TIME, TimeUnit.SECONDS);

            // 사용자 ID가 있다면 View 엔티티 저장
            if (userId != null) {
                Member member = memberRepository.findById(Long.parseLong(userId))
                    .orElseThrow(() -> new IllegalArgumentException("Member not found"));
                View view = View.builder()
                    .member(member)
                    .post(post)
                    .build();
                viewRepository.save(view);
            }
        }

        // 게시글의 상세 정보를 반환
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        return new PostDetailResponseDto(post);
    }

    @Override
    public void toggleLike(Long postId, String token) {

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        Long memberId = jwtTokenProvider.getMemberIdFromToken(token);

        Optional<PostLike> existingLike = postLikeRepository.findByPostIdAndMemberId(postId, memberId);

        if (existingLike.isPresent()) {
            // User has already liked the post, so remove the like
            post.decrementLikeCount();
            postRepository.save(post);
            postLikeRepository.delete(existingLike.get());
        } else {
            // User has not liked the post, so add a new like
            post.incrementLikeCount();
            postRepository.save(post);

            PostLike postLike = PostLike.builder()
                .postId(postId)
                .memberId(memberId)
                .build();
            postLikeRepository.save(postLike);
        }
    }
}
