package gift.member;

import gift.auth.JwtProvider;
import gift.auth.KakaoLoginClient;
import gift.auth.KakaoLoginProperties;
import gift.exception.AuthenticationException;
import gift.exception.DuplicateEntityException;
import gift.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final KakaoLoginClient kakaoLoginClient;
    private final KakaoLoginProperties kakaoLoginProperties;

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
        Member member = memberRepository.save(new Member(request.email(), request.password()));
        return jwtProvider.createToken(member.getEmail());
    }

    public String login(MemberRequest request) {
        Member member = memberRepository.findByEmail(request.email())
            .orElseThrow(() -> new AuthenticationException("Invalid email or password."));

        if (member.getPassword() == null || !member.getPassword().equals(request.password())) {
            throw new AuthenticationException("Invalid email or password.");
        }

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
        return memberRepository.save(new Member(email, password));
    }

    public Member findById(Long id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Member not found. id=" + id));
    }

    public void update(Long id, String email, String password) {
        Member member = findById(id);
        member.update(email, password);
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
