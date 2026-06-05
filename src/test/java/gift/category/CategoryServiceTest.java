package gift.category;

import gift.exception.EntityNotFoundException;
import gift.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryServiceTest {

    @Autowired
    private CategoryService categoryService;

    @Test
    void 카테고리를_추가한다() {
        CategoryRequest request = new CategoryRequest("도서", "#8B4513", "https://example.com/books.jpg", "소설, 기술서적");

        CategoryResponse saved = categoryService.addCategory(request);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("도서");
    }

    @Test
    void 전체_카테고리를_조회한다() {
        // V2 시드 데이터에 카테고리 3개 존재
        List<CategoryResponse> categories = categoryService.getAllCategories();

        assertThat(categories).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void 카테고리를_수정한다() {
        CategoryRequest request = new CategoryRequest("수정전", "#000000", "https://example.com/before.jpg", "설명");
        CategoryResponse saved = categoryService.addCategory(request);

        CategoryRequest updateRequest = new CategoryRequest("수정후", "#FFFFFF", "https://example.com/after.jpg", "수정 설명");
        CategoryResponse updated = categoryService.updateCategory(saved.id(), updateRequest);

        assertThat(updated.name()).isEqualTo("수정후");
        assertThat(updated.color()).isEqualTo("#FFFFFF");
    }

    @Test
    void 존재하지_않는_카테고리_조회_시_예외가_발생한다() {
        assertThatThrownBy(() -> categoryService.getCategory(999999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void 상품이_연결된_카테고리_삭제_시_예외가_발생한다() {
        // V2 시드 데이터에 카테고리 1에 상품이 연결되어 있음
        assertThatThrownBy(() -> categoryService.removeCategory(1L))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void 상품이_없는_카테고리는_삭제된다() {
        CategoryRequest request = new CategoryRequest("삭제용", "#123456", "https://example.com/del.jpg", "삭제 테스트");
        CategoryResponse saved = categoryService.addCategory(request);

        categoryService.removeCategory(saved.id());

        assertThatThrownBy(() -> categoryService.getCategory(saved.id()))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
