package gift.order;

import gift.IntegrationTest;
import gift.wish.WishRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest extends IntegrationTest {

    @Autowired
    private WishRepository wishRepository;

    @Test
    void 주문_목록을_조회한다() throws Exception {
        // given: V2 데이터에 user1(id=2)은 주문 2건
        var token = loginAndGetToken("user1@example.com", "password1");

        // when
        var result = mockMvc.perform(get("/api/orders")
            .header("Authorization", "Bearer " + token));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void 주문을_생성한다() throws Exception {
        // given: user1(id=2), 옵션 id=1 (맥북 프로 / 스페이스 블랙 / M1 Pro, 수량 10)
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = createRequest(1L, 1, "테스트 주문 메시지");

        // when
        var result = mockMvc.perform(post("/api/orders")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated())
            .andExpect(jsonPath("$.optionId").value(1))
            .andExpect(jsonPath("$.quantity").value(1))
            .andExpect(jsonPath("$.message").value("테스트 주문 메시지"));
    }

    @Test
    void 존재하지_않는_옵션으로_주문하면_404를_반환한다() throws Exception {
        // given
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = createRequest(999999L, 1, null);

        // when
        var result = mockMvc.perform(post("/api/orders")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isNotFound());
    }

    @Test
    void 유효하지_않은_토큰으로_주문하면_401을_반환한다() throws Exception {
        // given: 잘못된 JWT 토큰

        // when
        var result = mockMvc.perform(get("/api/orders")
            .header("Authorization", "Bearer invalid-token"));

        // then
        result.andExpect(status().isUnauthorized());
    }

    @Test
    void 재고_초과_주문시_400을_반환한다() throws Exception {
        // given: user1(id=2), 옵션 id=1 (수량 10) — 11개 주문하면 재고 초과
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = createRequest(1L, 11, "재고 초과 테스트");

        // when
        var result = mockMvc.perform(post("/api/orders")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    void 주문_생성_시_해당_상품의_위시가_삭제된다() throws Exception {
        // given: V2 데이터에 user1(id=2)은 product1(id=1)에 위시가 있음
        // option1(id=1)은 product1의 옵션
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = createRequest(1L, 1, "위시 클린업 테스트");

        assertThat(wishRepository.findByMemberIdAndProductId(2L, 1L)).isPresent();

        // when
        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // then: 위시가 삭제되어야 한다
        assertThat(wishRepository.findByMemberIdAndProductId(2L, 1L)).isEmpty();
    }

    private OrderRequest createRequest(Long optionId, int quantity, String message) {
        return new OrderRequest(optionId, quantity, message);
    }

}
