package gift.order;

import gift.auth.LoginMember;
import gift.member.Member;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<?> getOrders(
        @LoginMember Member member,
        Pageable pageable
    ) {
        var orders = orderService.findByMemberId(member.getId(), pageable).map(OrderResponse::from);
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
        @LoginMember Member member,
        @Valid @RequestBody OrderRequest request
    ) {
        Order saved = orderService.createOrder(member, request);

        return ResponseEntity.created(URI.create("/api/orders/" + saved.getId()))
            .body(OrderResponse.from(saved));
    }
}
