package gift.option;

import gift.ServiceTest;
import gift.exception.DuplicateEntityException;
import gift.exception.EntityNotFoundException;
import gift.exception.InsufficientStockException;
import gift.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionServiceTest extends ServiceTest {

    @Autowired
    private OptionService optionService;

    @Test
    void 상품의_옵션_목록을_조회한다() {
        List<OptionResponse> options = optionService.getProductOptions(1L);

        assertThat(options).isNotEmpty();
    }

    @Test
    void 존재하지_않는_상품의_옵션_조회_시_예외가_발생한다() {
        assertThatThrownBy(() -> optionService.getProductOptions(999999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void 옵션을_추가한다() {
        OptionResponse saved = optionService.addOption(1L, new OptionRequest("골드 / M1 Ultra", 3));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("골드 / M1 Ultra");
    }

    @Test
    void 중복_옵션명_추가_시_예외가_발생한다() {
        // V2 시드 데이터에 상품 1에 "스페이스 블랙 / M1 Pro" 옵션 존재
        assertThatThrownBy(() -> optionService.addOption(1L, new OptionRequest("스페이스 블랙 / M1 Pro", 5)))
            .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void 재고를_차감한다() {
        // V2 시드 데이터에 옵션 1의 수량은 10
        Option option = optionService.subtractQuantity(1L, 3);

        assertThat(option.getQuantity()).isEqualTo(7);
    }

    @Test
    void 재고_초과_차감_시_예외가_발생한다() {
        assertThatThrownBy(() -> optionService.subtractQuantity(1L, 999))
            .isInstanceOf(InsufficientStockException.class);
    }
}
