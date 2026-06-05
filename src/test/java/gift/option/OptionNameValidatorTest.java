package gift.option;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptionNameValidatorTest {

    @Test
    void 정상_이름은_에러가_없다() {
        List<String> errors = OptionNameValidator.validate("스페이스 블랙 / M1 Pro");

        assertThat(errors).isEmpty();
    }

    @Test
    void null이면_에러가_발생한다() {
        List<String> errors = OptionNameValidator.validate(null);

        assertThat(errors).hasSize(1);
    }

    @Test
    void 빈_문자열이면_에러가_발생한다() {
        List<String> errors = OptionNameValidator.validate("   ");

        assertThat(errors).hasSize(1);
    }

    @Test
    void 최대길이_초과하면_에러가_발생한다() {
        String longName = "a".repeat(51);
        List<String> errors = OptionNameValidator.validate(longName);

        assertThat(errors).isNotEmpty();
    }

    @Test
    void 허용되지_않는_특수문자가_포함되면_에러가_발생한다() {
        List<String> errors = OptionNameValidator.validate("옵션@이름");

        assertThat(errors).isNotEmpty();
    }
}
