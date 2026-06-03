package gift.auth;

import gift.member.Member;
import gift.member.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Transactional(readOnly = true)
public class KakaoAuthService {
    private static final Logger log = LoggerFactory.getLogger(KakaoAuthService.class);
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final KakaoLoginClient kakaoLoginClient;
    private final KakaoLoginProperties kakaoLoginProperties;

    public KakaoAuthService(
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

    public String buildKakaoAuthUrl() {
        return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", kakaoLoginProperties.clientId())
            .queryParam("redirect_uri", kakaoLoginProperties.redirectUri())
            .queryParam("scope", "account_email,talk_message")
            .build()
            .toUriString();
    }

    @Transactional
    public String kakaoLogin(String code) {
        KakaoLoginClient.KakaoTokenResponse kakaoToken = kakaoLoginClient.requestAccessToken(code);
        KakaoLoginClient.KakaoUserResponse kakaoUser = kakaoLoginClient.requestUserInfo(kakaoToken.accessToken());
        String email = kakaoUser.email();

        Member member = memberRepository.findByEmail(email)
            .orElseGet(() -> new Member(email));
        member.updateKakaoAccessToken(kakaoToken.accessToken());
        memberRepository.save(member);

        log.info("카카오 로그인 성공. email={}", member.getEmail());
        return jwtProvider.createToken(member.getEmail());
    }
}
