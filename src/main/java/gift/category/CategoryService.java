package gift.category;

import gift.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {
    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category getCategory(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("카테고리가 존재하지 않습니다. id=" + id));
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category addCategory(CategoryRequest request) {
        Category saved = categoryRepository.save(request.toEntity());
        log.info("카테고리 추가. id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    public Category updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("카테고리가 존재하지 않습니다. id=" + id));
        category.update(request.name(), request.color(), request.imageUrl(), request.description());
        log.info("카테고리 수정. id={}, name={}", id, request.name());
        return categoryRepository.save(category);
    }

    public void removeCategory(Long id) {
        getCategory(id);
        categoryRepository.deleteById(id);
        log.info("카테고리 삭제. id={}", id);
    }
}
