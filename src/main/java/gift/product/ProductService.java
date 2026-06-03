package gift.product;

import gift.category.Category;
import gift.category.CategoryService;
import gift.exception.EntityNotFoundException;
import gift.exception.ValidationException;
import gift.order.OrderRepository;
import gift.wish.WishRepository;
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
    private final WishRepository wishRepository;
    private final OrderRepository orderRepository;

    public ProductService(
        ProductRepository productRepository,
        CategoryService categoryService,
        WishRepository wishRepository,
        OrderRepository orderRepository
    ) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.wishRepository = wishRepository;
        this.orderRepository = orderRepository;
    }

    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
            .map(ProductResponse::from)
            .toList();
    }

    public List<AdminProductResponse> getAllAdminProducts() {
        return productRepository.findAll().stream()
            .map(AdminProductResponse::from)
            .toList();
    }

    public AdminProductResponse getAdminProduct(Long id) {
        return AdminProductResponse.from(getProduct(id));
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("상품이 존재하지 않습니다. id=" + id));
    }

    public ProductResponse getProductResponse(Long id) {
        return ProductResponse.from(getProduct(id));
    }

    public boolean existsByCategoryId(Long categoryId) {
        return productRepository.existsByCategoryId(categoryId);
    }

    private Category getCategoryById(Long id) {
        return categoryService.getCategory(id);
    }

    @Transactional
    public ProductResponse addProduct(ProductRequest request) {
        validateName(request.name());
        Category category = getCategoryById(request.categoryId());
        Product saved = productRepository.save(request.toEntity(category));
        log.info("상품 추가. id={}, name={}", saved.getId(), saved.getName());
        return ProductResponse.from(saved);
    }

    @Transactional
    public Product addProduct(String name, int price, String imageUrl, Long categoryId) {
        Category category = getCategoryById(categoryId);
        Product saved = productRepository.save(new Product(name, price, imageUrl, category));
        log.info("상품 추가. id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        validateName(request.name());
        Product product = getProduct(id);
        Category category = getCategoryById(request.categoryId());
        product.update(request.name(), request.price(), request.imageUrl(), category);
        log.info("상품 수정. id={}, name={}", id, request.name());
        return ProductResponse.from(product);
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
        // TODO: 위시 등록된 상품 삭제 정책 확인 필요 (cascade 삭제 vs 삭제 거부)
        // if (wishRepository.existsByProductId(id)) {
        //     throw new ValidationException("위시리스트에 등록된 상품은 삭제할 수 없습니다.");
        // }
        if (orderRepository.existsByOptionProductId(id)) {
            throw new ValidationException("주문 내역이 있는 상품은 삭제할 수 없습니다.");
        }
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
