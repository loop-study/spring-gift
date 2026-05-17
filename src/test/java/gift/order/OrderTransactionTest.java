package gift.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import gift.option.Option;
import gift.option.OptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderTransactionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OptionRepository optionRepository;

    @MockitoSpyBean
    private KakaoMessageClient kakaoMessageClient;

    @Test
    void 포인트_부족_시_재고가_롤백된다() throws Exception {
        // given: user1(point=5,000,000), option1(맥북 프로, price=3,360,000, quantity=10)
        // 2개 주문 → 총액 6,720,000 > 포인트 5,000,000 → 포인트 부족
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = new OrderRequest(1L, 2, "포인트 부족 테스트");

        int quantityBefore = optionRepository.findById(1L).orElseThrow().getQuantity();

        // when
        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        // then: 재고가 원래대로 유지되어야 한다
        Option option = optionRepository.findById(1L).orElseThrow();
        assertThat(option.getQuantity()).isEqualTo(quantityBefore);
    }

    @Test
    void 트랜잭션_롤백_시_카카오_메시지가_발송되지_않는다() throws Exception {
        // given: user1(point=5,000,000), option1(price=3,360,000, quantity=10)
        // 2개 주문 → 총액 6,720,000 > 포인트 5,000,000 → 포인트 부족 → 롤백
        var token = loginAndGetToken("user1@example.com", "password1");
        var request = new OrderRequest(1L, 2, "롤백 테스트");

        // when
        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        // then: 카카오 메시지가 발송되지 않아야 한다
        verify(kakaoMessageClient, never()).sendToMe(any(), any(), any());
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
