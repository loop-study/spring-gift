package gift.option;

import gift.product.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionTest {

    @Test
    void 음수_수량_차감_시_예외가_발생한다() {
        Product product = new Product("테스트 상품", 10000, "https://example.com/img.jpg", null);
        Option option = new Option(product, "기본 옵션", 10);

        assertThatThrownBy(() -> option.subtractQuantity(-5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 수량_0_차감_시_예외가_발생한다() {
        Product product = new Product("테스트 상품", 10000, "https://example.com/img.jpg", null);
        Option option = new Option(product, "기본 옵션", 10);

        assertThatThrownBy(() -> option.subtractQuantity(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
