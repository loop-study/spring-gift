package gift.order;

import gift.option.Option;
import gift.product.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @Test
    void 총액을_계산한다() {
        // given: 상품 가격 3,360,000 × 수량 2 = 6,720,000
        var product = new Product("맥북 프로", 3360000, "https://example.com/img.jpg", null);
        var option = new Option(product, "스페이스 블랙", 10);
        var order = new Order(option, 1L, 2, "테스트");

        // when
        int totalPrice = order.calculateTotalPrice();

        // then
        assertThat(totalPrice).isEqualTo(6720000);
    }
}
