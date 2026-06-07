package gift.option;

import gift.exception.DuplicateEntityException;
import gift.exception.EntityNotFoundException;
import gift.exception.ValidationException;
import gift.product.Product;
import gift.product.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OptionService {
    private final OptionRepository optionRepository;
    private final ProductService productService;

    public OptionService(OptionRepository optionRepository, ProductService productService) {
        this.optionRepository = optionRepository;
        this.productService = productService;
    }

    public List<OptionResponse> getProductOptions(Long productId) {
        productService.getProduct(productId);
        return optionRepository.findByProductId(productId).stream()
            .map(OptionResponse::from)
            .toList();
    }

    @Transactional
    public OptionResponse addOption(Long productId, OptionRequest request) {
        validateName(request.name());

        Product product = productService.getProduct(productId);

        if (optionRepository.existsByProductIdAndName(productId, request.name())) {
            throw new DuplicateEntityException("이미 존재하는 옵션명입니다.");
        }

        Option saved = optionRepository.save(new Option(product, request.name(), request.quantity()));
        return OptionResponse.from(saved);
    }

    @Transactional
    public Option subtractQuantity(Long optionId, int quantity) {
        Option option = optionRepository.findById(optionId)
            .orElseThrow(() -> new EntityNotFoundException("옵션이 존재하지 않습니다. id=" + optionId));
        option.subtractQuantity(quantity);
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
    }

    private void validateName(String name) {
        List<String> errors = OptionNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(", ", errors));
        }
    }
}
