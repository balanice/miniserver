package com.force.miniserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

@Component
@ServerEndpoint("/screen")
public class MiniWebSocket {

    private static final Logger logger = LoggerFactory.getLogger(MiniWebSocket.class);

    @OnOpen
    public void onOpen(Session session) {
        logger.info("onOpen");
        SpringContextUtil.getBean(DeviceService.class).startScreen(session);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        logger.info("message: {}", message);
        SpringContextUtil.getBean(DeviceService.class).handleAction(message);
    }

    @OnClose
    public void onClose(Session session) {
        logger.info("onClose");
        SpringContextUtil.getBean(DeviceService.class).stopScreen();
    }

    @OnError
    public void onError(Session session, Throwable t) {
        logger.error(t.getMessage(), t);
        SpringContextUtil.getBean(DeviceService.class).stopScreen();
    }
}
