package gift.product;

import gift.category.Category;
import gift.category.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    public Product findByIdOrThrow(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
    }

    public Category findCategoryById(Long id) {
        return categoryRepository.findById(id).orElse(null);
    }

    public Category findCategoryByIdOrThrow(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
    }

    public List<Category> findAllCategories() {
        return categoryRepository.findAll();
    }

    public Product create(ProductRequest request) {
        validateName(request.name());

        Category category = findCategoryById(request.categoryId());
        if (category == null) {
            return null;
        }

        return productRepository.save(request.toEntity(category));
    }

    public Product create(String name, int price, String imageUrl, Long categoryId) {
        Category category = findCategoryByIdOrThrow(categoryId);
        return productRepository.save(new Product(name, price, imageUrl, category));
    }

    public Product update(Long id, ProductRequest request) {
        validateName(request.name());

        Category category = findCategoryById(request.categoryId());
        if (category == null) {
            return null;
        }

        Product product = findById(id);
        if (product == null) {
            return null;
        }

        product.update(request.name(), request.price(), request.imageUrl(), category);
        return productRepository.save(product);
    }

    public void update(Long id, String name, int price, String imageUrl, Long categoryId) {
        Product product = findByIdOrThrow(id);
        Category category = findCategoryByIdOrThrow(categoryId);
        product.update(name, price, imageUrl, category);
        productRepository.save(product);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    private void validateName(String name) {
        List<String> errors = ProductNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }
}
