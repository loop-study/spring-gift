package gift.wish;

import gift.exception.EntityNotFoundException;
import gift.product.Product;
import gift.product.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class WishService {
    private final WishRepository wishRepository;
    private final ProductRepository productRepository;

    public WishService(WishRepository wishRepository, ProductRepository productRepository) {
        this.wishRepository = wishRepository;
        this.productRepository = productRepository;
    }

    public Page<Wish> findByMemberId(Long memberId, Pageable pageable) {
        return wishRepository.findByMemberId(memberId, pageable);
    }

    public Product findProductById(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("상품이 존재하지 않습니다. id=" + productId));
    }

    public Wish findByMemberIdAndProductId(Long memberId, Long productId) {
        return wishRepository.findByMemberIdAndProductId(memberId, productId).orElse(null);
    }

    public Wish save(Long memberId, Product product) {
        return wishRepository.save(new Wish(memberId, product));
    }

    public Wish findById(Long id) {
        return wishRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("위시가 존재하지 않습니다. id=" + id));
    }

    public void delete(Wish wish) {
        wishRepository.delete(wish);
    }
}
