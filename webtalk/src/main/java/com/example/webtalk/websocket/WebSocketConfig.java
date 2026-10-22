package com.example.webtalk.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 5.6 WebSocket 경로와 메시지, 6.4 핵심 서버 코드 뼈대를 구현한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        // 기본 동일 출처 검사: 접속한 서버의 도메인/IP와 같은 Origin만 허용한다.
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // SUBSCRIBE 시점에 방 구성원 여부를 확인한다 (5.7, 5.8 "다른 방 열람" 대응).
        registration.interceptors(webSocketAuthInterceptor);
    }
}
