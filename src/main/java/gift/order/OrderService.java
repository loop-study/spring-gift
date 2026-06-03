package gift.order;

import gift.member.Member;
import gift.member.MemberService;
import gift.option.Option;
import gift.option.OptionService;
import gift.wish.WishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final OptionService optionService;
    private final MemberService memberService;
    private final WishService wishService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
        OrderRepository orderRepository,
        OptionService optionService,
        MemberService memberService,
        WishService wishService,
        ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.optionService = optionService;
        this.memberService = memberService;
        this.wishService = wishService;
        this.eventPublisher = eventPublisher;
    }

    public Page<OrderResponse> getMemberOrders(Long memberId, Pageable pageable) {
        return orderRepository.findByMemberId(memberId, pageable).map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse createOrder(Member member, OrderRequest request) {
        // subtract stock
        Option option = optionService.subtractQuantity(request.optionId(), request.quantity());

        // save order
        Order order = new Order(option, member.getId(), request.quantity(), request.message());

        // deduct points (domain calculates total price)
        int totalPrice = order.calculateTotalPrice();
        memberService.deductPoint(member.getId(), totalPrice);

        Order saved = orderRepository.save(order);
        log.info("주문 생성. orderId={}, memberId={}, optionId={}, quantity={}, totalPrice={}",
            saved.getId(), member.getId(), request.optionId(), request.quantity(), totalPrice);

        // remove wish if exists
        wishService.removeByMemberAndProduct(member.getId(), option.getProduct().getId());

        // publish event for post-commit notification
        eventPublisher.publishEvent(
            new OrderCreatedEvent(member.getKakaoAccessToken(), saved, option.getProduct()));

        return OrderResponse.from(saved);
    }
}
