package com.sunmoon.appserver.transport.http;

import com.sunmoon.common.reprocess.ReprocessService;
import com.sunmoon.common.reprocess.ReprocessViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 실패한 것을 보고 되돌리는 자리 — Device Server 의 {@code /reprocess} 와 <b>같은 모양</b>입니다.
 *
 * <p>모양은 공통 라이브러리(sun-moon-java-common 의 {@link ReprocessViews})가 만들고, 여기는 내보내기만
 * 합니다. BO 재처리 화면이 Device Server·Order·이 서비스를 불러 한 화면에 모읍니다
 * (umbrella docs/design/reprocess-library.md).
 *
 * <p><b>호출하는 쪽은 BO 뿐입니다.</b> 지금 8086 은 LAN 전용이라 관리 토큰을 두지 않았습니다. 테이블 QR 로
 * 8086 을 외부에 여는 날, 이 주소는 nginx 에서 막거나 Device Server 처럼 관리 토큰을 받아야 합니다 —
 * 그렇지 않으면 인터넷의 누구든 "다시"를 누를 수 있습니다.
 */
@RestController
@RequestMapping("/reprocess")
@Tag(name = "Reprocess", description = "실패 재처리 — BO 재처리 화면이 읽습니다")
public class ReprocessController {

    private final ReprocessService reprocess;

    public ReprocessController(ReprocessService reprocess) {
        this.reprocess = reprocess;
    }

    @GetMapping
    @Operation(summary = "지금 손봐야 할 것 전부",
            description = "storeId 를 비우면 전 매장입니다. 실패가 없는 종류도 count 0 으로 보입니다.")
    public Map<String, Object> list(@RequestParam(required = false) String storeId) {
        return ReprocessViews.list(reprocess, storeId);
    }

    @PostMapping("/one")
    @Operation(summary = "한 건 다시",
            description = "못 찾은 것도 200 입니다 — 목록을 열어 둔 사이에 처리됐을 수 있습니다(accepted=false).")
    public Map<String, Object> retryOne(@RequestParam String kind, @RequestParam String id,
                                        @RequestParam(required = false) String storeId) {
        return ReprocessViews.one(kind, id, reprocess.retry(kind, storeId, id));
    }

    @PostMapping("/all")
    @Operation(summary = "그 종류 전부 다시")
    public Map<String, Object> retryAll(@RequestParam String kind,
                                        @RequestParam(required = false) String storeId) {
        return ReprocessViews.all(kind, reprocess.retryAll(kind, storeId));
    }
}
