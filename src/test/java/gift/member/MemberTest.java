package gift.member;

import gift.exception.InsufficientPointException;
import gift.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    @Test
    void 포인트를_충전한다() {
        Member member = new Member("test@example.com", "password");
        member.chargePoint(1000);

        assertThat(member.getPoint()).isEqualTo(1000);
    }

    @Test
    void 포인트_충전_금액이_0이면_예외가_발생한다() {
        Member member = new Member("test@example.com", "password");

        assertThatThrownBy(() -> member.chargePoint(0))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void 포인트_충전_금액이_음수이면_예외가_발생한다() {
        Member member = new Member("test@example.com", "password");

        assertThatThrownBy(() -> member.chargePoint(-1000))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void 포인트를_차감한다() {
        Member member = new Member("test@example.com", "password");
        member.chargePoint(5000);
        member.deductPoint(3000);

        assertThat(member.getPoint()).isEqualTo(2000);
    }

    @Test
    void 포인트_차감_금액이_0이면_예외가_발생한다() {
        Member member = new Member("test@example.com", "password");
        member.chargePoint(5000);

        assertThatThrownBy(() -> member.deductPoint(0))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void 포인트_차감_금액이_음수이면_예외가_발생한다() {
        Member member = new Member("test@example.com", "password");
        member.chargePoint(5000);

        assertThatThrownBy(() -> member.deductPoint(-1000))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void 포인트가_부족하면_예외가_발생한다() {
        Member member = new Member("test@example.com", "password");
        member.chargePoint(1000);

        assertThatThrownBy(() -> member.deductPoint(5000))
            .isInstanceOf(InsufficientPointException.class);
    }
}
