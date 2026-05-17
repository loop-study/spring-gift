package gift.product;

import gift.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerTest extends IntegrationTest {

    private static final Long EXISTING_CATEGORY_ID = 1L;

    @Test
    void 상품_목록을_조회한다() throws Exception {
        // given: V2 데이터에 6개 상품 존재

        // when
        var result = mockMvc.perform(get("/api/products"));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void 상품을_단건_조회한다() throws Exception {
        // given: V2 데이터에 id=1 맥북 프로 16인치 존재

        // when
        var result = mockMvc.perform(get("/api/products/1"));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("맥북 프로 16인치"))
            .andExpect(jsonPath("$.price").value(3360000));
    }

    @Test
    void 상품을_생성한다() throws Exception {
        // given
        var request = createRequest("갤럭시 S25", 1200000, "https://example.com/galaxy.jpg", EXISTING_CATEGORY_ID);

        // when
        var result = mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("갤럭시 S25"))
            .andExpect(jsonPath("$.price").value(1200000))
            .andExpect(jsonPath("$.categoryId").value(EXISTING_CATEGORY_ID));
    }

    @Test
    void 상품을_수정한다() throws Exception {
        // given
        var id = createProductAndGetId("수정전 상품", 10000, "https://example.com/before.jpg", EXISTING_CATEGORY_ID);
        var updateRequest = createRequest("수정후 상품", 20000, "https://example.com/after.jpg", EXISTING_CATEGORY_ID);

        // when
        var result = mockMvc.perform(put("/api/products/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("수정후 상품"))
            .andExpect(jsonPath("$.price").value(20000));
    }

    @Test
    void 상품을_삭제한다() throws Exception {
        // given
        var id = createProductAndGetId("삭제용 상품", 5000, "https://example.com/del.jpg", EXISTING_CATEGORY_ID);

        // when
        var result = mockMvc.perform(delete("/api/products/" + id));

        // then
        result.andExpect(status().isNoContent());
    }

    @Test
    void 존재하지_않는_상품을_조회하면_404를_반환한다() throws Exception {
        // given: 존재하지 않는 id

        // when
        var result = mockMvc.perform(get("/api/products/999999"));

        // then
        result.andExpect(status().isNotFound())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
    }

    @Test
    void 상품명이_15자를_초과하면_400을_반환한다() throws Exception {
        // given
        var request = createRequest("이름이열다섯자를초과하는상품이름입니다", 10000, "https://example.com/long.jpg", EXISTING_CATEGORY_ID);

        // when
        var result = mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    void 상품명에_카카오가_포함되면_400을_반환한다() throws Exception {
        // given
        var request = createRequest("카카오 선물", 10000, "https://example.com/kakao.jpg", EXISTING_CATEGORY_ID);

        // when
        var result = mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_카테고리로_생성하면_404를_반환한다() throws Exception {
        // given
        var request = createRequest("테스트 상품", 10000, "https://example.com/test.jpg", 999999L);

        // when
        var result = mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isNotFound());
    }

    private ProductRequest createRequest(String name, int price, String imageUrl, Long categoryId) {
        return new ProductRequest(name, price, imageUrl, categoryId);
    }

    private Long createProductAndGetId(String name, int price, String imageUrl, Long categoryId) throws Exception {
        var request = createRequest(name, price, imageUrl, categoryId);
        var response = mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(response.getResponse().getContentAsString()).get("id").asLong();
    }
}
