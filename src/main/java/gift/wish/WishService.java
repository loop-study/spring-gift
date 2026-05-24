package gift.wish;

import gift.exception.EntityNotFoundException;
import gift.exception.ForbiddenException;
import gift.product.Product;
import gift.product.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class WishService {
    private final WishRepository wishRepository;
    private final ProductService productService;

    public WishService(WishRepository wishRepository, ProductService productService) {
        this.wishRepository = wishRepository;
        this.productService = productService;
    }

    public Page<Wish> getMemberWishes(Long memberId, Pageable pageable) {
        return wishRepository.findByMemberId(memberId, pageable);
    }

    public Optional<Wish> findByMemberAndProduct(Long memberId, Long productId) {
        productService.getProduct(productId);
        return wishRepository.findByMemberIdAndProductId(memberId, productId);
    }

    public Wish addWish(Long memberId, Long productId) {
        Product product = productService.getProduct(productId);
        return wishRepository.save(new Wish(memberId, product));
    }

    public void deleteByIdAndMemberId(Long id, Long memberId) {
        Wish wish = getWish(id);
        if (!wish.getMemberId().equals(memberId)) {
            throw new ForbiddenException("다른 사용자의 위시를 삭제할 수 없습니다.");
        }
        wishRepository.delete(wish);
    }

    public Wish getWish(Long id) {
        return wishRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("위시가 존재하지 않습니다. id=" + id));
    }

    public void removeByMemberAndProduct(Long memberId, Long productId) {
        wishRepository.findByMemberIdAndProductId(memberId, productId)
            .ifPresent(wishRepository::delete);
    }
}
