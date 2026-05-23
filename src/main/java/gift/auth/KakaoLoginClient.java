package gift.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import gift.exception.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoLoginClient {
    private static final Logger log = LoggerFactory.getLogger(KakaoLoginClient.class);
    private final KakaoLoginProperties properties;
    private final RestClient restClient;

    public KakaoLoginClient(KakaoLoginProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public KakaoTokenResponse requestAccessToken(String code) {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", properties.clientId());
        params.add("redirect_uri", properties.redirectUri());
        params.add("code", code);
        params.add("client_secret", properties.clientSecret());

        try {
            log.info("카카오 토큰 교환 요청");
            KakaoTokenResponse response = restClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(params)
                .retrieve()
                .body(KakaoTokenResponse.class);
            log.info("카카오 토큰 교환 성공");
            return response;
        } catch (RestClientResponseException e) {
            log.warn("카카오 토큰 교환 실패. status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AuthenticationException("카카오 로그인에 실패했습니다.");
        } catch (ResourceAccessException e) {
            log.error("카카오 서버 연결 실패", e);
            throw new AuthenticationException("카카오 로그인에 실패했습니다.");
        }
    }

    public KakaoUserResponse requestUserInfo(String accessToken) {
        try {
            log.info("카카오 사용자 정보 조회 요청");
            KakaoUserResponse response = restClient.get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserResponse.class);
            log.info("카카오 사용자 정보 조회 성공. email={}", response.email());
            return response;
        } catch (RestClientResponseException e) {
            log.warn("카카오 사용자 정보 조회 실패. status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AuthenticationException("카카오 로그인에 실패했습니다.");
        } catch (ResourceAccessException e) {
            log.error("카카오 서버 연결 실패", e);
            throw new AuthenticationException("카카오 로그인에 실패했습니다.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoUserResponse(@JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

        public String email() {
            return kakaoAccount.email();
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record KakaoAccount(String email) {
        }
    }
}
