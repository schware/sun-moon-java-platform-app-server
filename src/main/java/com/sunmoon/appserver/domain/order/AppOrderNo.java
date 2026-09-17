package com.sunmoon.appserver.domain.order;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 업무 주문번호 — 사람이 부르고 화면에 뜨는 번호 (업무 정의서 §4.4.1).
 *
 * <p><b>내부 PK와 다른 값입니다.</b> 문서가 권한 방향이 그것입니다:
 * 업무번호에 점포를 넣으면 운영자가 읽기 편하지만, 점포 코드가 바뀌거나
 * 길이가 문제될 때 PK까지 흔들리면 안 됩니다. 그래서 PK는 그냥 숫자이고
 * 이 번호는 그 위에 따로 붙습니다.
 *
 * <p>모양은 <b>점포 + 판매일자 + 일련번호</b> 입니다.
 * 문서는 앞에 브랜드를 두지만 <b>BO에 브랜드 마스터가 아직 없습니다</b>.
 * 지어낸 자리를 미리 비워두지 않는 이유는, 그 번호가 이미 손님 화면에
 * 뜨고 영수증에 찍히기 때문입니다 — 나중에 브랜드가 생기면 그날부터
 * 발급되는 번호에 붙고, 이미 나간 번호는 그대로 둡니다.
 *
 * <p>일련번호가 판매일자로 갈리는 것은 Order의 수령번호와 같은 이유입니다:
 * 매일 1번부터 다시 시작해야 번호가 끝없이 길어지지 않고, 어제 번호가
 * 오늘 화면에 섞이지 않습니다.
 */
public final class AppOrderNo {

    /** 하이픈을 뺀 판매일자. 번호 안에서 구분자가 겹치면 되짚어 읽기 어려워집니다. */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 네 자리. 한 매장이 하루 만 건을 넘기면 자리가 늘어나는데, 그때는
     * 번호가 길어질 뿐 깨지지는 않습니다 — 0을 채우는 것은 자릿수를
     * 맞추려는 것이지 상한을 두는 것이 아닙니다.
     */
    private static final int SEQ_DIGITS = 4;

    private AppOrderNo() {
    }

    public static String of(String storeId, LocalDate businessDate, int seq) {
        return storeId + "-" + DAY.format(businessDate) + "-"
                + String.format("%0" + SEQ_DIGITS + "d", seq);
    }
}
