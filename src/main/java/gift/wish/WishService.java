package gift.wish;

import gift.exception.EntityNotFoundException;
import gift.exception.ForbiddenException;
import gift.product.Product;
import gift.product.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishService {
    private static final Logger log = LoggerFactory.getLogger(WishService.class);
    private final WishRepository wishRepository;
    private final ProductService productService;

    public WishService(WishRepository wishRepository, ProductService productService) {
        this.wishRepository = wishRepository;
        this.productService = productService;
    }

    public Page<WishResponse> getMemberWishes(Long memberId, Pageable pageable) {
        return wishRepository.findByMemberId(memberId, pageable).map(WishResponse::from);
    }

    @Transactional
    public WishResponse addWish(Long memberId, Long productId) {
        Wish wish = wishRepository.findByMemberIdAndProductId(memberId, productId)
            .orElseGet(() -> {
                Product product = productService.getProduct(productId);
                Wish saved = wishRepository.save(new Wish(memberId, product));
                log.info("위시 추가. memberId={}, productId={}", memberId, productId);
                return saved;
            });
        return WishResponse.from(wish);
    }

    @Transactional
    public void deleteByIdAndMemberId(Long id, Long memberId) {
        Wish wish = getWish(id);
        if (!wish.getMemberId().equals(memberId)) {
            log.warn("위시 삭제 실패 — 소유권 불일치. wishId={}, memberId={}", id, memberId);
            throw new ForbiddenException("다른 사용자의 위시를 삭제할 수 없습니다.");
        }
        wishRepository.delete(wish);
        log.info("위시 삭제. wishId={}, memberId={}", id, memberId);
    }

    public Wish getWish(Long id) {
        return wishRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("위시가 존재하지 않습니다. id=" + id));
    }

    @Transactional
    public void removeByMemberAndProduct(Long memberId, Long productId) {
        wishRepository.findByMemberIdAndProductId(memberId, productId)
            .ifPresent(wishRepository::delete);
    }
}
