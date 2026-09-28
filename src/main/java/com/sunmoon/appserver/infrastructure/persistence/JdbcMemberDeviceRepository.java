package com.sunmoon.appserver.infrastructure.persistence;

import com.sunmoon.appserver.domain.member.MemberDeviceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** app_server {@code member_device} (V4). */
@Repository
public class JdbcMemberDeviceRepository implements MemberDeviceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberDeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(String memberNo, String tokenHash, String platform) {
        jdbcTemplate.update("INSERT INTO member_device (member_no, token_hash, platform) VALUES (?, ?, ?)",
                memberNo, tokenHash, platform);
    }

    @Override
    public Optional<String> memberOf(String tokenHash) {
        return jdbcTemplate.queryForList(
                "SELECT member_no FROM member_device WHERE token_hash = ? AND revoked_at IS NULL",
                String.class, tokenHash).stream().findFirst();
    }

    @Override
    public void touch(String tokenHash, Instant at) {
        jdbcTemplate.update("UPDATE member_device SET last_seen = ? WHERE token_hash = ?", Timestamp.from(at), tokenHash);
    }

    @Override
    public boolean revoke(String tokenHash, Instant at) {
        return jdbcTemplate.update(
                "UPDATE member_device SET revoked_at = ?, push_token = NULL WHERE token_hash = ? AND revoked_at IS NULL",
                Timestamp.from(at), tokenHash) > 0;
    }

    @Override
    @Transactional
    public boolean setPushToken(String tokenHash, String pushToken) {
        // 같은 휴대폰이 다시 설치되면 같은 푸시 토큰이 새 기기 줄로 옵니다 — 옛 줄에서 뗍니다.
        jdbcTemplate.update("UPDATE member_device SET push_token = NULL WHERE push_token = ? AND token_hash <> ?",
                pushToken, tokenHash);
        return jdbcTemplate.update(
                "UPDATE member_device SET push_token = ? WHERE token_hash = ? AND revoked_at IS NULL",
                pushToken, tokenHash) > 0;
    }

    @Override
    public void clearPushToken(String pushToken) {
        jdbcTemplate.update("UPDATE member_device SET push_token = NULL WHERE push_token = ?", pushToken);
    }

    @Override
    public List<String> pushTokens(String memberNo) {
        return jdbcTemplate.queryForList(
                "SELECT push_token FROM member_device WHERE member_no = ? AND revoked_at IS NULL AND push_token IS NOT NULL",
                String.class, memberNo);
    }
}
