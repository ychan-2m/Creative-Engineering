package com.example.webtalk.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
public class RoomChangeNotifier {
    private static final Logger log = LoggerFactory.getLogger(RoomChangeNotifier.class);
    private final SimpMessagingTemplate messaging;

    public RoomChangeNotifier(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener
    public void notifyMembers(RoomChangedEvent event) {
        try {
            // 수신자는 원래 트랜잭션에서 확정한다. 커밋 콜백에서 DB 연결을 추가로 얻지 않는다.
            for (String loginId : event.memberLoginIds()) {
                messaging.convertAndSendToUser(loginId, "/queue/rooms", Map.of("roomId", event.roomId()));
            }
        } catch (RuntimeException ex) {
            // 메시지 저장은 이미 커밋되었다. 알림 실패는 주기 조회로 복구한다.
            log.warn("대화 목록 갱신 알림 실패: roomId={}", event.roomId(), ex);
        }
    }
}
