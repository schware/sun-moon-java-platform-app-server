package com.sunmoon.appserver.transport.http;

import com.sunmoon.appserver.domain.entry.EntryRefusal;
import com.sunmoon.appserver.domain.entry.TableEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 테이블 QR 진입 — 손님이 코드를 찍고 메뉴를 보기 전에 거치는 문.
 *
 * <p>여기를 통과하면 화면은 {@code GET /menus} 로 메뉴를 받습니다. 둘을
 * 한 번에 내려주지 않는 것은, 게이트가 하는 일이 "이 자리에서 지금 주문이
 * 되는가"뿐이기 때문입니다. 메뉴는 그 다음 질문이고, 매장별 메뉴가 생기면
 * 그때 이 결과를 입력으로 받게 됩니다.
 */
@RestController
@RequestMapping("/t")
@Tag(name = "Table entry", description = "테이블 QR 진입 게이트")
public class TableEntryController {

    private final TableEntryService service;

    public TableEntryController(TableEntryService service) {
        this.service = service;
    }

    /**
     * 상태 코드를 이유별로 나눕니다. 손님 화면이 "다시 시도" 버튼을 보일지
     * 직원을 부르라고 할지는 이것으로 갈립니다.
     */
    @GetMapping("/{qrCode}")
    @Operation(summary = "QR 진입",
            description = "코드·테이블·매장·영업 여부를 한 번에 확인하고, 통과하면 "
                    + "매장과 자리와 판매일자를 돌려줍니다. 메뉴는 GET /menus 로 따로.")
    @ApiResponse(responseCode = "200", description = "통과")
    @ApiResponse(responseCode = "404", description = "이 코드로 찾아지는 테이블이 없습니다")
    @ApiResponse(responseCode = "409", description = "자리가 중지됐거나 매장이 영업 중이 아닙니다")
    @ApiResponse(responseCode = "503", description = "영업 여부를 확인하지 못했습니다 — 닫힌 것과 다릅니다")
    public ResponseEntity<?> enter(@PathVariable String qrCode) {
        TableEntryService.Result result = service.enter(qrCode);
        if (result.allowed()) {
            return ResponseEntity.ok(result.entry());
        }

        EntryRefusal refusal = result.refusal();
        return ResponseEntity.status(statusOf(refusal)).body(body(refusal));
    }

    private static HttpStatus statusOf(EntryRefusal refusal) {
        return switch (refusal) {
            case UNKNOWN_CODE -> HttpStatus.NOT_FOUND;
            case TABLE_INACTIVE, UNKNOWN_STORE, STORE_CLOSED -> HttpStatus.CONFLICT;
            // 503: 지금은 안 되지만 잠시 후엔 될 수 있습니다. 4xx로 주면
            // 화면이 "다시 시도"를 안 보여줍니다.
            case STORE_UNREACHABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private static Map<String, String> body(EntryRefusal refusal) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("reason", refusal.name());
        body.put("message", refusal.message());
        return body;
    }
}
