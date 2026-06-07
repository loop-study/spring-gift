package gift.category;

import gift.exception.EntityNotFoundException;
import gift.exception.ValidationException;
import gift.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("카테고리가 존재하지 않습니다. id=" + id));
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
            .map(CategoryResponse::from)
            .toList();
    }

    @Transactional
    public CategoryResponse addCategory(CategoryRequest request) {
        Category saved = categoryRepository.save(request.toEntity());
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("카테고리가 존재하지 않습니다. id=" + id));
        category.update(request.name(), request.color(), request.imageUrl(), request.description());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void removeCategory(Long id) {
        getCategory(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ValidationException("카테고리에 등록된 상품이 있어 삭제할 수 없습니다.");
        }
        categoryRepository.deleteById(id);
    }
}
