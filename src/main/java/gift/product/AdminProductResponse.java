package gift.product;

public record AdminProductResponse(
    Long id,
    String name,
    int price,
    String imageUrl,
    Long categoryId,
    String categoryName
) {
    public static AdminProductResponse from(Product product) {
        return new AdminProductResponse(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getImageUrl(),
            product.getCategory().getId(),
            product.getCategory().getName()
        );
    }
}
