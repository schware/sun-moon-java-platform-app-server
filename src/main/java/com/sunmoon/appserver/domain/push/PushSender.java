package com.sunmoon.appserver.domain.push;

/** 한 기기에 알림 한 통. FCM 이 없으면 아무것도 안 하는 구현이 들어갑니다. */
public interface PushSender {

    enum Result { SENT, UNREGISTERED, FAILED, OFF }

    Result send(String pushToken, PushComposer.Message message);
}
