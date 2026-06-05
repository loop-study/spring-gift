package gift.product;

import gift.exception.EntityNotFoundException;
import gift.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Test
    void 상품_목록을_페이징_조회한다() {
        Page<ProductResponse> products = productService.getAllProducts(PageRequest.of(0, 10));

        assertThat(products.getContent()).isNotEmpty();
    }

    @Test
    void 상품을_단건_조회한다() {
        ProductResponse product = productService.getProductResponse(1L);

        assertThat(product.id()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_상품_조회_시_예외가_발생한다() {
        assertThatThrownBy(() -> productService.getProduct(999999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void 상품을_추가한다() {
        ProductRequest request = new ProductRequest("갤럭시 S25", 1200000, "https://example.com/galaxy.jpg", 1L);

        ProductResponse saved = productService.addProduct(request);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.name()).isEqualTo("갤럭시 S25");
    }

    @Test
    void 카카오가_포함된_상품명은_예외가_발생한다() {
        ProductRequest request = new ProductRequest("카카오 선물", 10000, "https://example.com/kakao.jpg", 1L);

        assertThatThrownBy(() -> productService.addProduct(request))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void 상품을_수정한다() {
        ProductRequest request = new ProductRequest("수정된 상품", 20000, "https://example.com/updated.jpg", 1L);

        ProductResponse updated = productService.updateProduct(1L, request);

        assertThat(updated.name()).isEqualTo("수정된 상품");
        assertThat(updated.price()).isEqualTo(20000);
    }

    @Test
    void 위시가_연결된_상품_삭제_시_예외가_발생한다() {
        // V2 시드 데이터에 상품 1에 위시가 연결되어 있음
        assertThatThrownBy(() -> productService.removeProduct(1L))
            .isInstanceOf(ValidationException.class);
    }
}
