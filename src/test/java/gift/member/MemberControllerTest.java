package gift.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 회원가입에_성공하면_JWT를_반환한다() throws Exception {
        // given
        var request = createRequest("newuser@test.com", "password123");

        // when
        var result = mockMvc.perform(post("/api/members/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void 중복_이메일로_회원가입하면_400을_반환한다() throws Exception {
        // given: V2 데이터에 admin@example.com이 존재
        var request = createRequest("admin@example.com", "anypassword");

        // when
        var result = mockMvc.perform(post("/api/members/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    void 로그인에_성공하면_JWT를_반환한다() throws Exception {
        // given: V2 데이터에 admin@example.com / admin1234 존재
        var request = createRequest("admin@example.com", "admin1234");

        // when
        var result = mockMvc.perform(post("/api/members/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void 잘못된_비밀번호로_로그인하면_400을_반환한다() throws Exception {
        // given
        var request = createRequest("admin@example.com", "wrongpassword");

        // when
        var result = mockMvc.perform(post("/api/members/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_이메일로_로그인하면_400을_반환한다() throws Exception {
        // given
        var request = createRequest("nobody@test.com", "password123");

        // when
        var result = mockMvc.perform(post("/api/members/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // then
        result.andExpect(status().isBadRequest());
    }

    private MemberRequest createRequest(String email, String password) {
        return new MemberRequest(email, password);
    }
}
