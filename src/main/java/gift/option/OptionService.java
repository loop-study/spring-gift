package gift.option;

import gift.exception.DuplicateEntityException;
import gift.exception.EntityNotFoundException;
import gift.exception.ValidationException;
import gift.product.Product;
import gift.product.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OptionService {
    private static final Logger log = LoggerFactory.getLogger(OptionService.class);
    private final OptionRepository optionRepository;
    private final ProductService productService;

    public OptionService(OptionRepository optionRepository, ProductService productService) {
        this.optionRepository = optionRepository;
        this.productService = productService;
    }

    public List<Option> getProductOptions(Long productId) {
        productService.getProduct(productId);
        return optionRepository.findByProductId(productId);
    }

    @Transactional
    public OptionResponse addOption(Long productId, OptionRequest request) {
        validateName(request.name());

        Product product = productService.getProduct(productId);

        if (optionRepository.existsByProductIdAndName(productId, request.name())) {
            throw new DuplicateEntityException("이미 존재하는 옵션명입니다.");
        }

        Option saved = optionRepository.save(new Option(product, request.name(), request.quantity()));
        log.info("옵션 추가. optionId={}, productId={}, name={}", saved.getId(), productId, request.name());
        return OptionResponse.from(saved);
    }

    @Transactional
    public Option subtractQuantity(Long optionId, int quantity) {
        Option option = optionRepository.findById(optionId)
            .orElseThrow(() -> new EntityNotFoundException("옵션이 존재하지 않습니다. id=" + optionId));
        option.subtractQuantity(quantity);
        log.info("재고 차감. optionId={}, quantity={}, remaining={}", optionId, quantity, option.getQuantity());
        return option;
    }

    @Transactional
    public void removeOption(Long productId, Long optionId) {
        productService.getProduct(productId);

        List<Option> options = optionRepository.findByProductId(productId);
        if (options.size() <= 1) {
            throw new ValidationException("옵션이 1개인 상품은 옵션을 삭제할 수 없습니다.");
        }

        Option option = optionRepository.findById(optionId)
            .orElseThrow(() -> new EntityNotFoundException("옵션이 존재하지 않습니다. id=" + optionId));

        if (!option.getProduct().getId().equals(productId)) {
            throw new EntityNotFoundException("해당 상품의 옵션이 아닙니다. optionId=" + optionId);
        }

        optionRepository.delete(option);
        log.info("옵션 삭제. optionId={}, productId={}", optionId, productId);
    }

    private void validateName(String name) {
        List<String> errors = OptionNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(", ", errors));
        }
    }
}
