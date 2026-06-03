package gift.product;

import gift.category.Category;
import gift.category.CategoryService;
import gift.exception.EntityNotFoundException;
import gift.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    public ProductService(ProductRepository productRepository, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
    }

    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("상품이 존재하지 않습니다. id=" + id));
    }

    private Category getCategoryById(Long id) {
        return categoryService.getCategory(id);
    }

    @Transactional
    public Product addProduct(ProductRequest request) {
        validateName(request.name());
        Category category = getCategoryById(request.categoryId());
        Product saved = productRepository.save(request.toEntity(category));
        log.info("상품 추가. id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public Product addProduct(String name, int price, String imageUrl, Long categoryId) {
        Category category = getCategoryById(categoryId);
        Product saved = productRepository.save(new Product(name, price, imageUrl, category));
        log.info("상품 추가. id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest request) {
        validateName(request.name());
        Product product = getProduct(id);
        Category category = getCategoryById(request.categoryId());
        product.update(request.name(), request.price(), request.imageUrl(), category);
        log.info("상품 수정. id={}, name={}", id, request.name());
        return product;
    }

    @Transactional
    public void updateProduct(Long id, String name, int price, String imageUrl, Long categoryId) {
        Product product = getProduct(id);
        Category category = getCategoryById(categoryId);
        product.update(name, price, imageUrl, category);
        log.info("상품 수정. id={}, name={}", id, name);
    }

    @Transactional
    public void removeProduct(Long id) {
        getProduct(id);
        productRepository.deleteById(id);
        log.info("상품 삭제. id={}", id);
    }

    private void validateName(String name) {
        List<String> errors = ProductNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(", ", errors));
        }
    }
}
