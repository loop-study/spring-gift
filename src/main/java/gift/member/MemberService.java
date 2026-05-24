package gift.member;

import gift.auth.JwtProvider;
import gift.auth.KakaoLoginClient;
import gift.auth.KakaoLoginProperties;
import gift.exception.AuthenticationException;
import gift.exception.DuplicateEntityException;
import gift.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
public class MemberService {
    private static final Logger log = LoggerFactory.getLogger(MemberService.class);
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final KakaoLoginClient kakaoLoginClient;
    private final KakaoLoginProperties kakaoLoginProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberService(
        MemberRepository memberRepository,
        JwtProvider jwtProvider,
        KakaoLoginClient kakaoLoginClient,
        KakaoLoginProperties kakaoLoginProperties
    ) {
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
        this.kakaoLoginClient = kakaoLoginClient;
        this.kakaoLoginProperties = kakaoLoginProperties;
    }

    // --- REST API (MemberController) ---

    public String register(MemberRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new DuplicateEntityException("Email is already registered.");
        }
        String encoded = passwordEncoder.encode(request.password());
        Member member = memberRepository.save(new Member(request.email(), encoded));
        log.info("회원가입 성공. email={}", member.getEmail());
        return jwtProvider.createToken(member.getEmail());
    }

    public String login(MemberRequest request) {
        Member member = memberRepository.findByEmail(request.email())
            .orElseThrow(() -> {
                log.warn("로그인 실패 — 존재하지 않는 이메일. email={}", request.email());
                return new AuthenticationException("Invalid email or password.");
            });

        if (member.getPassword() == null || !passwordEncoder.matches(request.password(), member.getPassword())) {
            log.warn("로그인 실패 — 비밀번호 불일치. email={}", request.email());
            throw new AuthenticationException("Invalid email or password.");
        }

        log.info("로그인 성공. email={}", member.getEmail());
        return jwtProvider.createToken(member.getEmail());
    }

    // --- Kakao OAuth (KakaoAuthController) ---

    public String buildKakaoAuthUrl() {
        return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", kakaoLoginProperties.clientId())
            .queryParam("redirect_uri", kakaoLoginProperties.redirectUri())
            .queryParam("scope", "account_email,talk_message")
            .build()
            .toUriString();
    }

    public String kakaoLogin(String code) {
        KakaoLoginClient.KakaoTokenResponse kakaoToken = kakaoLoginClient.requestAccessToken(code);
        KakaoLoginClient.KakaoUserResponse kakaoUser = kakaoLoginClient.requestUserInfo(kakaoToken.accessToken());
        String email = kakaoUser.email();

        Member member = memberRepository.findByEmail(email)
            .orElseGet(() -> new Member(email));
        member.updateKakaoAccessToken(kakaoToken.accessToken());
        memberRepository.save(member);

        return jwtProvider.createToken(member.getEmail());
    }

    // --- Admin (AdminMemberController) ---

    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    public Member createMember(String email, String password) {
        return memberRepository.save(new Member(email, passwordEncoder.encode(password)));
    }

    public Member findById(Long id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Member not found. id=" + id));
    }

    public void update(Long id, String email, String password) {
        Member member = findById(id);
        member.update(email, passwordEncoder.encode(password));
        memberRepository.save(member);
    }

    public void deductPoint(Long id, int amount) {
        Member member = findById(id);
        member.deductPoint(amount);
        memberRepository.save(member);
    }

    public void chargePoint(Long id, int amount) {
        Member member = findById(id);
        member.chargePoint(amount);
        memberRepository.save(member);
    }

    public void delete(Long id) {
        memberRepository.deleteById(id);
    }
}
