package gift.order;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderNotificationListener {
    private final KakaoMessageClient kakaoMessageClient;

    public OrderNotificationListener(KakaoMessageClient kakaoMessageClient) {
        this.kakaoMessageClient = kakaoMessageClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (event.kakaoAccessToken() == null) {
            return;
        }
        try {
            kakaoMessageClient.sendToMe(event.kakaoAccessToken(), event.order(), event.product());
        } catch (Exception ignored) {
        }
    }
}
