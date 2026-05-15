package gift.wish;

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
        return productRepository.findById(productId).orElse(null);
    }

    public Wish findByMemberIdAndProductId(Long memberId, Long productId) {
        return wishRepository.findByMemberIdAndProductId(memberId, productId).orElse(null);
    }

    public Wish save(Long memberId, Product product) {
        return wishRepository.save(new Wish(memberId, product));
    }

    public Wish findById(Long id) {
        return wishRepository.findById(id).orElse(null);
    }

    public void delete(Wish wish) {
        wishRepository.delete(wish);
    }
}
