package gift.wish;

import gift.ServiceTest;
import gift.exception.EntityNotFoundException;
import gift.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WishServiceTest extends ServiceTest {

    @Autowired
    private WishService wishService;

    @Test
    void 위시_목록을_조회한다() {
        // V2 시드 데이터에 user1(id=2)은 위시 2개
        Page<WishResponse> wishes = wishService.getMemberWishes(2L, PageRequest.of(0, 10));

        assertThat(wishes.getContent()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void 위시를_추가한다() {
        // user1(id=2)이 상품 3에 위시 추가
        WishResponse wish = wishService.addWish(2L, 3L);

        assertThat(wish.productId()).isEqualTo(3L);
    }

    @Test
    void 이미_존재하는_위시는_기존_위시를_반환한다() {
        // V2 시드 데이터에 user1(id=2)은 상품 1에 위시 존재
        WishResponse wish = wishService.addWish(2L, 1L);

        assertThat(wish.productId()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_상품에_위시_추가_시_예외가_발생한다() {
        assertThatThrownBy(() -> wishService.addWish(2L, 999999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void 다른_사람의_위시_삭제_시_예외가_발생한다() {
        // V2 시드 데이터에 위시 1은 user1(id=2) 소유
        assertThatThrownBy(() -> wishService.deleteByIdAndMemberId(1L, 999L))
            .isInstanceOf(ForbiddenException.class);
    }
}
