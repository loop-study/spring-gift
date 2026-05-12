package gift.wish;

import gift.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WishControllerTest extends IntegrationTest {

    @Test
    void 위시리스트를_조회한다() throws Exception {
        // given: V2 데이터에 user1은 위시 2개 (상품 1, 3)
        var token = loginAndGetToken("user1@example.com", "password1");

        // when
        var result = mockMvc.perform(get("/api/wishes")
            .header("Authorization", "Bearer " + token));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void 위시를_추가한다() throws Exception {
        // given: user1은 상품 2를 위시에 안 넣었음
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = createRequest(2L);

        // when
        var result = mockMvc.perform(post("/api/wishes")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated())
            .andExpect(jsonPath("$.productId").value(2));
    }

    @Test
    void 중복_위시를_추가하면_기존_위시를_반환한다() throws Exception {
        // given: user1은 이미 상품 1을 위시에 넣었음
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = createRequest(1L);

        // when
        var result = mockMvc.perform(post("/api/wishes")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    void 위시를_삭제한다() throws Exception {
        // given: V2 데이터에 user1의 위시 id=1 (상품 1)
        var token = loginAndGetToken("user1@example.com", "password1");

        // when
        var result = mockMvc.perform(delete("/api/wishes/1")
            .header("Authorization", "Bearer " + token));

        // then
        result.andExpect(status().isNoContent());
    }

    @Test
    void 다른_사용자의_위시를_삭제하면_403을_반환한다() throws Exception {
        // given: 위시 id=1은 user1의 것, user2로 로그인
        var token = loginAndGetToken("user2@example.com", "password2");

        // when
        var result = mockMvc.perform(delete("/api/wishes/1")
            .header("Authorization", "Bearer " + token));

        // then
        result.andExpect(status().isForbidden());
    }

    @Test
    void 유효하지_않은_토큰으로_위시를_조회하면_401을_반환한다() throws Exception {
        // given: 잘못된 JWT 토큰

        // when
        var result = mockMvc.perform(get("/api/wishes")
            .header("Authorization", "Bearer invalid-token"));

        // then
        result.andExpect(status().isUnauthorized());
    }

    @Test
    void 존재하지_않는_상품에_위시를_추가하면_404를_반환한다() throws Exception {
        // given
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = createRequest(999999L);

        // when
        var result = mockMvc.perform(post("/api/wishes")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isNotFound());
    }

    private WishRequest createRequest(Long productId) {
        return new WishRequest(productId);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        var loginRequest = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        var result = mockMvc.perform(post("/api/members/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginRequest))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
