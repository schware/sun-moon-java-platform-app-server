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

    public MemberController(MemberService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "회원번호 발급",
            description = "아무것도 묻지 않고 번호 하나를 냅니다. 모양은 "
                    + "날짜(6) + 그날 순번(4) + 검증숫자(1) 입니다. "
                    + "전화번호를 안 받는 이유는 개인정보이기 때문이고, "
                    + "가입을 안 만드는 이유는 필요한 것에 비해 크기 때문입니다.")
    @ApiResponse(responseCode = "201", description = "새 회원번호")
    public ResponseEntity<?> issue() {
        Member member = service.issue();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "memberNo", member.memberNo(),
                "issuedAt", member.issuedAt().toString()));
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
