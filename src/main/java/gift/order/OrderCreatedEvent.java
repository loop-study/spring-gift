package gift.order;

import gift.product.Product;

public record OrderCreatedEvent(
    String kakaoAccessToken,
    Order order,
    Product product
) {
}
