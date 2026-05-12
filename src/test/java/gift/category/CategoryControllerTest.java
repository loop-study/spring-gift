package gift.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 카테고리_목록을_조회한다() throws Exception {
        // given: V2 마이그레이션으로 3개 카테고리(전자기기, 패션, 식품)가 존재

        // when
        var result = mockMvc.perform(get("/api/categories"));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void 카테고리를_생성한다() throws Exception {
        // given
        var request = createRequest("도서", "#8B4513", "https://example.com/books.jpg", "소설, 기술서적");

        // when
        var result = mockMvc.perform(post("/api/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("도서"))
            .andExpect(jsonPath("$.color").value("#8B4513"))
            .andExpect(jsonPath("$.imageUrl").value("https://example.com/books.jpg"))
            .andExpect(jsonPath("$.description").value("소설, 기술서적"));
    }

    @Test
    void 카테고리를_수정한다() throws Exception {
        // given
        var id = createCategoryAndGetId("수정전", "#000000", "https://example.com/before.jpg", "수정 전 설명");
        var updateRequest = createRequest("수정후", "#FFFFFF", "https://example.com/after.jpg", "수정 후 설명");

        // when
        var result = mockMvc.perform(put("/api/categories/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("수정후"))
            .andExpect(jsonPath("$.color").value("#FFFFFF"));
    }

    @Test
    void 카테고리를_삭제한다() throws Exception {
        // given
        var id = createCategoryAndGetId("삭제용", "#123456", "https://example.com/del.jpg", "삭제 테스트");

        // when
        var result = mockMvc.perform(delete("/api/categories/" + id));

        // then
        result.andExpect(status().isNoContent());
    }

    @Test
    void 존재하지_않는_카테고리를_수정하면_404를_반환한다() throws Exception {
        // given
        var request = createRequest("없는카테고리", "#000000", "https://example.com/none.jpg", "없음");

        // when
        var result = mockMvc.perform(put("/api/categories/999999")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isNotFound());
    }

    private CategoryRequest createRequest(String name, String color, String imageUrl, String description) {
        return new CategoryRequest(name, color, imageUrl, description);
    }

    private Long createCategoryAndGetId(String name, String color, String imageUrl, String description) throws Exception {
        var request = createRequest(name, color, imageUrl, description);
        var response = mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(response.getResponse().getContentAsString()).get("id").asLong();
    }
}
