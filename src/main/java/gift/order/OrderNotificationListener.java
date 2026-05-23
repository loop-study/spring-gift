package gift.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);
    private final KakaoMessageClient kakaoMessageClient;

    public OrderNotificationListener(KakaoMessageClient kakaoMessageClient) {
        this.kakaoMessageClient = kakaoMessageClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (event.kakaoAccessToken() == null) {
            log.debug("카카오 액세스 토큰 없음 — 메시지 발송 생략. orderId={}", event.order().getId());
            return;
        }
        try {
            kakaoMessageClient.sendToMe(event.kakaoAccessToken(), event.order(), event.product());
            log.info("카카오 메시지 발송 성공. orderId={}", event.order().getId());
        } catch (Exception e) {
            log.warn("카카오 메시지 발송 실패. orderId={}", event.order().getId(), e);
        }
    }
}
