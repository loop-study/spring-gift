package gift.product;

import gift.category.Category;
import gift.category.CategoryService;
import gift.exception.EntityNotFoundException;
import gift.exception.ValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {
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


    public Product addProduct(ProductRequest request) {
        validateName(request.name());
        Category category = getCategoryById(request.categoryId());
        return productRepository.save(request.toEntity(category));
    }

    public Product addProduct(String name, int price, String imageUrl, Long categoryId) {
        Category category = getCategoryById(categoryId);
        return productRepository.save(new Product(name, price, imageUrl, category));
    }

    public Product updateProduct(Long id, ProductRequest request) {
        validateName(request.name());
        Product product = getProduct(id);
        Category category = getCategoryById(request.categoryId());
        product.update(request.name(), request.price(), request.imageUrl(), category);
        return productRepository.save(product);
    }

    public void updateProduct(Long id, String name, int price, String imageUrl, Long categoryId) {
        Product product = getProduct(id);
        Category category = getCategoryById(categoryId);
        product.update(name, price, imageUrl, category);
        productRepository.save(product);
    }

    public void removeProduct(Long id) {
        productRepository.deleteById(id);
    }

    private void validateName(String name) {
        List<String> errors = ProductNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(", ", errors));
        }
    }
}
