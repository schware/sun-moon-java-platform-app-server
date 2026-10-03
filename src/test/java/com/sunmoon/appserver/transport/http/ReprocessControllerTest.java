package com.sunmoon.appserver.transport.http;

import com.sunmoon.appserver.config.ReprocessConfig;
import com.sunmoon.common.reprocess.FailedItem;
import com.sunmoon.common.reprocess.ReprocessService;
import com.sunmoon.common.reprocess.Reprocessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BO 가 Device Server 와 같은 타입으로 읽는지 — 특히 failedAt 이 숫자가 아니라 ISO 문자열인지.
 *
 * <p>Spring 의 Jackson 설정(Boot 기본값)을 거쳐야 그걸 볼 수 있어서 WebMvcTest 입니다. 손으로 만든
 * MockMvc 는 날짜를 숫자로 써서, 실제와 다른 답을 시험하게 됩니다.
 */
@WebMvcTest(ReprocessController.class)
@Import({ReprocessConfig.class, ReprocessControllerTest.SampleKind.class})
class ReprocessControllerTest {

    @TestConfiguration
    static class SampleKind {
        @Bean
        Reprocessor sample() {
            return new Reprocessor() {
                @Override public String kind() { return "SAMPLE"; }
                @Override public String label() { return "시험"; }
                @Override public List<FailedItem> failed(String storeId) {
                    return List.of(new FailedItem("12", "store-01", "짜장면 외 1건", "닿지 못함", 1,
                            Instant.parse("2026-10-03T11:00:00Z")));
                }
                @Override public Outcome retry(String storeId, String id) {
                    return "12".equals(id) ? Outcome.done("다시 했습니다") : Outcome.notFound(id);
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void listHasTheSharedShape() throws Exception {
        mvc.perform(get("/reprocess").param("storeId", "store-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value("store-01"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.kinds[0].kind").value("SAMPLE"))
                .andExpect(jsonPath("$.kinds[0].count").value(1))
                .andExpect(jsonPath("$.kinds[0].items[0].failedAt").value("2026-10-03T11:00:00Z"));
    }

    @Test
    void oneAnswers200EvenWhenNotFound() throws Exception {
        mvc.perform(post("/reprocess/one").param("kind", "SAMPLE").param("id", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false));
        mvc.perform(post("/reprocess/one").param("kind", "SAMPLE").param("id", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));
    }

    @Test
    void allCountsQueued() throws Exception {
        mvc.perform(post("/reprocess/all").param("kind", "SAMPLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void noKindsStillAnswers() throws Exception {
        MockMvcBuilders.standaloneSetup(new ReprocessController(new ReprocessService(List.of()))).build()
                .perform(get("/reprocess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeId").value(""))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.kinds").isEmpty());
    }
}
