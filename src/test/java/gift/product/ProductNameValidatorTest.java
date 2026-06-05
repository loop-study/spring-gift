package gift.product;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductNameValidatorTest {

    @Test
    void 정상_이름은_에러가_없다() {
        List<String> errors = ProductNameValidator.validate("맥북 프로");

        assertThat(errors).isEmpty();
    }

    @Test
    void null이면_에러가_발생한다() {
        List<String> errors = ProductNameValidator.validate(null);

        assertThat(errors).hasSize(1);
    }

    @Test
    void 빈_문자열이면_에러가_발생한다() {
        List<String> errors = ProductNameValidator.validate("   ");

        assertThat(errors).hasSize(1);
    }

    @Test
    void 최대길이_초과하면_에러가_발생한다() {
        List<String> errors = ProductNameValidator.validate("이름이열다섯자를초과하는상품이름입니다");

        assertThat(errors).isNotEmpty();
    }

    @Test
    void 허용되지_않는_특수문자가_포함되면_에러가_발생한다() {
        List<String> errors = ProductNameValidator.validate("상품@이름");

        assertThat(errors).isNotEmpty();
    }

    @Test
    void 카카오가_포함되면_에러가_발생한다() {
        List<String> errors = ProductNameValidator.validate("카카오 선물");

        assertThat(errors).isNotEmpty();
    }

    @Test
    void 관리자_모드에서는_카카오가_허용된다() {
        List<String> errors = ProductNameValidator.validate("카카오 선물", true);

        assertThat(errors).isEmpty();
    }
}
