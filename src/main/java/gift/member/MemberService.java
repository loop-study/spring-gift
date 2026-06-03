package gift.member;

import gift.exception.DuplicateEntityException;
import gift.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberService {
    private static final Logger log = LoggerFactory.getLogger(MemberService.class);
    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, BCryptPasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Member register(MemberRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new DuplicateEntityException("이미 등록된 이메일입니다.");
        }
        String encoded = passwordEncoder.encode(request.password());
        Member member = memberRepository.save(new Member(request.email(), encoded));
        log.info("회원가입 성공. email={}", member.getEmail());
        return member;
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    public boolean existsByEmail(String email) {
        return memberRepository.existsByEmail(email);
    }

    @Transactional
    public Member createMember(String email, String password) {
        return memberRepository.save(new Member(email, passwordEncoder.encode(password)));
    }

    public Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다. email=" + email));
    }

    public Member getMember(Long id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("회원이 존재하지 않습니다. id=" + id));
    }

    @Transactional
    public void updateMember(Long id, String email, String password) {
        Member member = getMember(id);
        member.update(email, passwordEncoder.encode(password));
    }

    @Transactional
    public void deductPoint(Long id, int amount) {
        Member member = getMember(id);
        member.deductPoint(amount);
    }

    @Transactional
    public void chargePoint(Long id, int amount) {
        Member member = getMember(id);
        member.chargePoint(amount);
    }

    @Transactional
    public void removeMember(Long id) {
        getMember(id);
        memberRepository.deleteById(id);
    }
}
