package com.sunmoon.appserver.transport.http;

import com.sunmoon.appserver.domain.member.Member;
import com.sunmoon.appserver.domain.member.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 회원번호 (업무 정의서 §4.1).
 *
 * <p><b>회원가입이 아닙니다.</b> 이름도 전화번호도 묻지 않고 번호만
 * 내줍니다. 손님은 그것을 적어 두었다가 다음에 칩니다 — 우리가 아는
 * 것은 "같은 번호를 댄 사람"까지입니다.
 */
@RestController
@RequestMapping("/members")
@Tag(name = "Members", description = "회원번호 — 가입 없이 같은 손님인지만 압니다")
public class MemberController {

    private final MemberService service;
    private final com.sunmoon.appserver.domain.member.MemberDevices devices;
    private final com.sunmoon.appserver.domain.member.MemberOrders history;

    public MemberController(MemberService service,
                            com.sunmoon.appserver.domain.member.MemberDevices devices,
                            com.sunmoon.appserver.domain.member.MemberOrders history) {
        this.service = service;
        this.devices = devices;
        this.history = history;
    }

    /** 본문 {@code {platform}} — WEB | ANDROID | IOS. 없으면 WEB. */
    public record IssueRequest(String platform) {
    }

    public record LinkRequest(String memberNo, String claimToken, String platform) {
    }

    public record PushTokenRequest(String pushToken) {
    }

    @PostMapping("/link")
    @Operation(summary = "영수증으로 새 기기 연결",
            description = "그 회원번호로 주문한 영수증의 QR 토큰을 가진 사람이 곧 그 번호의 사람입니다. "
                    + "맞으면 이 기기의 회원 토큰. 같은 번호로 10분에 5번 틀리면 1시간 막습니다(423).")
    @ApiResponse(responseCode = "200", description = "{memberToken}")
    @ApiResponse(responseCode = "404", description = "번호와 영수증이 맞지 않습니다")
    public ResponseEntity<?> link(@org.springframework.web.bind.annotation.RequestBody LinkRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "memberNo, claimToken 이 필요합니다"));
        }
        var link = devices.link(request.memberNo(), request.claimToken(), request.platform());
        return switch (link.result()) {
            case LINKED -> ResponseEntity.ok(Map.of("memberToken", link.token()));
            case NOT_OURS -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "회원번호와 영수증이 맞지 않습니다"));
            case LOCKED -> ResponseEntity.status(423)
                    .body(Map.of("error", "여러 번 틀려 잠시 막혔습니다. 한 시간 뒤 다시 하십시오"));
        };
    }

    @GetMapping("/me")
    @Operation(summary = "이 기기의 회원번호", description = "Authorization: Member <token>")
    public ResponseEntity<?> me(@org.springframework.web.bind.annotation.RequestHeader(
            value = "Authorization", required = false) String authorization) {
        return devices.memberOf(authorization).flatMap(service::verify)
                .<ResponseEntity<?>>map(m -> ResponseEntity.ok(Map.of(
                        "memberNo", m.memberNo(), "issuedAt", m.issuedAt().toString())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "이 기기에 회원 토큰이 없습니다")));
    }

    @GetMapping("/me/orders")
    @Operation(summary = "내 주문",
            description = "토큰이 있는 기기만. 상태는 오늘 주문만 매장(OMS)에 물어 채우고, 지난 주문은 원장 상태.")
    public ResponseEntity<?> myOrders(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false)
            String authorization,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int limit) {
        return devices.memberOf(authorization)
                .<ResponseEntity<?>>map(memberNo -> ResponseEntity.ok(history.of(memberNo, limit)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "이 기기에 회원 토큰이 없습니다")));
    }

    @PostMapping("/me/push-token")
    @Operation(summary = "푸시 토큰 등록 (앱)", description = "같은 푸시 토큰이 다른 기기 줄에 있으면 거기서 떼고 여기에.")
    public ResponseEntity<?> pushToken(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false)
            String authorization,
            @org.springframework.web.bind.annotation.RequestBody PushTokenRequest request) {
        if (devices.memberOf(authorization).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "이 기기에 회원 토큰이 없습니다"));
        }
        return devices.setPushToken(authorization, request == null ? null : request.pushToken())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.badRequest().body(Map.of("error", "pushToken 이 필요합니다"));
    }

    @PostMapping("/me/logout")
    @Operation(summary = "이 기기에서 빼기", description = "이 기기의 토큰만 끝냅니다. 번호와 다른 기기는 그대로.")
    public ResponseEntity<?> logout(@org.springframework.web.bind.annotation.RequestHeader(
            value = "Authorization", required = false) String authorization) {
        return devices.logout(authorization)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "이 기기에 회원 토큰이 없습니다"));
    }

    @PostMapping
    @Operation(summary = "회원번호 발급",
            description = "아무것도 묻지 않고 번호 하나를 냅니다. 모양은 "
                    + "날짜(6) + 그날 순번(4) + 검증숫자(1) 입니다. "
                    + "전화번호를 안 받는 이유는 개인정보이기 때문이고, "
                    + "가입을 안 만드는 이유는 필요한 것에 비해 크기 때문입니다.")
    @ApiResponse(responseCode = "201", description = "새 회원번호")
    public ResponseEntity<?> issue(@org.springframework.web.bind.annotation.RequestBody(required = false)
                                   IssueRequest request) {
        Member member = service.issue();
        // 번호를 받은 이 기기가 이력을 볼 수 있게 토큰도 같이 (customer-app §3.4).
        String token = devices.issueToken(member.memberNo(), request == null ? null : request.platform());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "memberNo", member.memberNo(),
                "issuedAt", member.issuedAt().toString(),
                "memberToken", token));
    }

    @GetMapping("/{memberNo}")
    @Operation(summary = "회원번호 확인",
            description = "우리가 준 번호인지 봅니다. 검증숫자가 먼저 오타를 거르므로, "
                    + "한 자리 잘못 친 번호는 데이터베이스까지 가지 않습니다. "
                    + "개인정보는 애초에 없어서 돌려줄 것이 번호와 발급 시각뿐입니다.")
    @ApiResponse(responseCode = "200", description = "우리가 준 번호입니다")
    @ApiResponse(responseCode = "404", description = "모르는 번호 — 모양이 틀린 것도 여기로 옵니다")
    public ResponseEntity<?> verify(@PathVariable String memberNo) {
        return service.verify(memberNo)
                .<ResponseEntity<?>>map(m -> ResponseEntity.ok(Map.of(
                        "memberNo", m.memberNo(),
                        "issuedAt", m.issuedAt().toString())))
                // 모양이 틀린 것과 없는 것을 같은 답으로 냅니다. 나누면
                // "이건 있을 수 있는 번호"라는 정보가 새 나가고, 그건
                // 번호를 지어내려는 쪽에만 쓸모가 있습니다.
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "회원번호를 찾을 수 없습니다")));
    }
}
