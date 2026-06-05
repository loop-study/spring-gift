package gift.order;

import gift.ServiceTest;
import gift.member.Member;
import gift.member.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceTest extends ServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private MemberService memberService;

    @Test
    void 주문_목록을_조회한다() {
        // V2 시드 데이터에 user1(id=2)은 주문 2건
        Page<OrderResponse> orders = orderService.getMemberOrders(2L, PageRequest.of(0, 10));

        assertThat(orders.getContent()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void 주문을_생성한다() {
        Member member = memberService.getMember(2L);
        OrderRequest request = new OrderRequest(1L, 1, "테스트 주문");

        OrderResponse saved = orderService.createOrder(member, request);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.quantity()).isEqualTo(1);
    }
}
