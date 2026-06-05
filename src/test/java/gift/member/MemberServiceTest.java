package gift.member;

import gift.exception.DuplicateEntityException;
import gift.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberServiceTest {

    @Autowired
    private MemberService memberService;

    @Test
    void 회원가입에_성공한다() {
        MemberRequest request = new MemberRequest("newuser@test.com", "password123");

        Member member = memberService.register(request);

        assertThat(member.getId()).isNotNull();
        assertThat(member.getEmail()).isEqualTo("newuser@test.com");
    }

    @Test
    void 중복_이메일로_가입하면_예외가_발생한다() {
        // V2 시드 데이터에 admin@example.com 존재
        MemberRequest request = new MemberRequest("admin@example.com", "password");

        assertThatThrownBy(() -> memberService.register(request))
            .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void 이메일로_회원을_조회한다() {
        Member member = memberService.getMemberByEmail("admin@example.com");

        assertThat(member.getEmail()).isEqualTo("admin@example.com");
    }

    @Test
    void 존재하지_않는_이메일_조회_시_예외가_발생한다() {
        assertThatThrownBy(() -> memberService.getMemberByEmail("nobody@test.com"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void 포인트를_충전한다() {
        // V2 시드 데이터에 user1(id=2) 존재
        Member before = memberService.getMember(2L);
        int pointBefore = before.getPoint();

        memberService.chargePoint(2L, 1000);

        Member after = memberService.getMember(2L);
        assertThat(after.getPoint()).isEqualTo(pointBefore + 1000);
    }

    @Test
    void 포인트를_차감한다() {
        Member before = memberService.getMember(2L);
        int pointBefore = before.getPoint();

        memberService.deductPoint(2L, 1000);

        Member after = memberService.getMember(2L);
        assertThat(after.getPoint()).isEqualTo(pointBefore - 1000);
    }
}
