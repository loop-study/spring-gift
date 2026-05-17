package gift.order;

import gift.exception.EntityNotFoundException;
import gift.member.Member;
import gift.member.MemberRepository;
import gift.option.Option;
import gift.option.OptionRepository;
import gift.wish.WishRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OptionRepository optionRepository;
    private final MemberRepository memberRepository;
    private final WishRepository wishRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
        OrderRepository orderRepository,
        OptionRepository optionRepository,
        MemberRepository memberRepository,
        WishRepository wishRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.optionRepository = optionRepository;
        this.memberRepository = memberRepository;
        this.wishRepository = wishRepository;
        this.eventPublisher = eventPublisher;
    }

    public Page<Order> findByMemberId(Long memberId, Pageable pageable) {
        return orderRepository.findByMemberId(memberId, pageable);
    }

    @Transactional
    public Order createOrder(Member member, OrderRequest request) {
        // validate option
        Option option = optionRepository.findById(request.optionId())
            .orElseThrow(() -> new EntityNotFoundException("옵션이 존재하지 않습니다. id=" + request.optionId()));

        // subtract stock
        option.subtractQuantity(request.quantity());
        optionRepository.save(option);

        // save order
        Order order = new Order(option, member.getId(), request.quantity(), request.message());

        // deduct points (domain calculates total price)
        member.deductPoint(order.calculateTotalPrice());
        memberRepository.save(member);

        Order saved = orderRepository.save(order);

        // remove wish if exists
        wishRepository.findByMemberIdAndProductId(member.getId(), option.getProduct().getId())
            .ifPresent(wishRepository::delete);

        // publish event for post-commit notification
        eventPublisher.publishEvent(
            new OrderCreatedEvent(member.getKakaoAccessToken(), saved, option.getProduct()));

        return saved;
    }
}
