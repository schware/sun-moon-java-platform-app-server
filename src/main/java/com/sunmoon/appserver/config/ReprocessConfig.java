package com.sunmoon.appserver.config;

import com.sunmoon.common.reprocess.ReprocessService;
import com.sunmoon.common.reprocess.Reprocessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 실패 재처리 — 이 서비스에 있는 {@link Reprocessor} 를 모두 모읍니다.
 *
 * <p>{@link ObjectProvider} 로 받는 이유: 종류가 하나도 없어도 떠야 합니다. 그때 {@code /reprocess} 는
 * {@code total 0, kinds []} 로 답하고, BO 재처리 화면은 "이 서비스는 볼 것이 없다"로 읽습니다.
 */
@Configuration
public class ReprocessConfig {

    @Bean
    public ReprocessService reprocessService(ObjectProvider<Reprocessor> kinds) {
        return new ReprocessService(kinds.orderedStream().toList());
    }
}
