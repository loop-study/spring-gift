package gift.auth;

import gift.exception.AuthenticationException;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.member.MemberRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(MemberRepository memberRepository, JwtProvider jwtProvider, BCryptPasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public String createToken(String email) {
        return jwtProvider.createToken(email);
    }

    public String login(MemberRequest request) {
        Member member = memberRepository.findByEmail(request.email())
            .orElseThrow(() -> {
                log.warn("로그인 실패 — 존재하지 않는 이메일. email={}", request.email());
                return new AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.");
            });

        if (member.getPassword() == null || !passwordEncoder.matches(request.password(), member.getPassword())) {
            log.warn("로그인 실패 — 비밀번호 불일치. email={}", request.email());
            throw new AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        log.info("로그인 성공. email={}", member.getEmail());
        return jwtProvider.createToken(member.getEmail());
    }
}
