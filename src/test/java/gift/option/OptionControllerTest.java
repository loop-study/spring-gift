package gift.option;

import gift.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OptionControllerTest extends IntegrationTest {

    @Test
    void 상품의_옵션_목록을_조회한다() throws Exception {
        // given: V2 데이터에 상품 1(맥북 프로)은 옵션 2개

        // when
        var result = mockMvc.perform(get("/api/products/1/options"));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 옵션을_생성한다() throws Exception {
        // given
        var request = createRequest("골드 / M1 Ultra", 3);

        // when
        var result = mockMvc.perform(post("/api/products/1/options")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("골드 / M1 Ultra"))
            .andExpect(jsonPath("$.quantity").value(3));
    }

    @Test
    void 중복_옵션명으로_생성하면_409를_반환한다() throws Exception {
        // given: V2 데이터에 상품 1에 "스페이스 블랙 / M1 Pro" 존재
        var request = createRequest("스페이스 블랙 / M1 Pro", 5);

        // when
        var result = mockMvc.perform(post("/api/products/1/options")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isConflict());
    }

    @Test
    void 옵션명이_50자를_초과하면_400을_반환한다() throws Exception {
        // given
        var longName = "a".repeat(51);
        var request = createRequest(longName, 1);

        // when
        var result = mockMvc.perform(post("/api/products/1/options")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    void 옵션이_2개_이상이면_삭제할_수_있다() throws Exception {
        // given: 상품 1(맥북 프로)은 옵션 2개 — id=1, id=2

        // when
        var result = mockMvc.perform(delete("/api/products/1/options/1"));

        // then
        result.andExpect(status().isNoContent());
    }

    @Test
    void 옵션이_1개인_상품에서_삭제하면_400을_반환한다() throws Exception {
        // given: 상품 3(나이키)은 옵션 1개 — id=5

        // when
        var result = mockMvc.perform(delete("/api/products/3/options/5"));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_상품의_옵션을_조회하면_404를_반환한다() throws Exception {
        // given: 존재하지 않는 상품 id

        // when
        var result = mockMvc.perform(get("/api/products/999999/options"));

        // then
        result.andExpect(status().isNotFound());
    }

    private OptionRequest createRequest(String name, int quantity) {
        return new OptionRequest(name, quantity);
    }
}
