package com.sunmoon.appserver.infrastructure.persistence;

import com.sunmoon.appserver.domain.member.Member;
import com.sunmoon.appserver.domain.member.MemberRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;

/** 회원번호의 JDBC 저장소 (업무 정의서 §4.1). */
@Repository
public class JdbcMemberRepository implements MemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@code app_order_seq} 와 같은 방식입니다 — 한 문장 안에서 올리고
     * 돌려받습니다. 읽고-더하고-쓰면 두 손님이 같은 순간에 받을 때
     * 같은 번호가 나옵니다.
     */
    @Override
    public int nextSeq(LocalDate businessDate) {
        Integer seq = jdbcTemplate.queryForObject("""
                INSERT INTO member_seq (business_date, last_seq)
                VALUES (?, 1)
                ON CONFLICT (business_date)
                DO UPDATE SET last_seq = member_seq.last_seq + 1
                RETURNING last_seq
                """, Integer.class, Date.valueOf(businessDate));
        return seq == null ? 1 : seq;
    }

    @Override
    public void save(Member member) {
        jdbcTemplate.update("""
                INSERT INTO member (member_no, business_date, seq, issued_at)
                VALUES (?, ?, ?, ?)
                """,
                member.memberNo(), Date.valueOf(member.businessDate()),
                member.seq(), Timestamp.from(member.issuedAt()));
    }

    @Override
    public Optional<Member> find(String memberNo) {
        return jdbcTemplate.query("""
                SELECT member_no, business_date, seq, issued_at
                  FROM member WHERE member_no = ?
                """,
                (rs, rowNum) -> new Member(
                        rs.getString("member_no"),
                        rs.getDate("business_date").toLocalDate(),
                        rs.getInt("seq"),
                        rs.getTimestamp("issued_at").toInstant()),
                memberNo).stream().findFirst();
    }
}
