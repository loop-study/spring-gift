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
        return ResponseEntity.ok(orderService.getMemberOrders(member.getId(), pageable));
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
        @LoginMember Member member,
        @Valid @RequestBody OrderRequest request
    ) {
        OrderResponse saved = orderService.createOrder(member, request);
        return ResponseEntity.created(URI.create("/api/orders/" + saved.id()))
            .body(saved);
    }
}
