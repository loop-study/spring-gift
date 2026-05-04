package gift.product;

import gift.category.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductRequest(
    @NotBlank String name,
    @Positive int price,
    @NotBlank String imageUrl,
    @NotNull Long categoryId
) {
    public Product toEntity(Category category) {
        return new Product(name, price, imageUrl, category);
    }
}
