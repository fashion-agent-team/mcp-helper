package com.fashionagent.helper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** helper.requests. 상태 바꾸기는 WHERE status = 'pending' 조건으로 한 번만 되게 한다(답 두 번, 만료와 답이 겹칠 때). */
@Repository
public class RequestRepository {

    private static final String SELECT = """
            SELECT r.id, r.user_id, r.contact_id, c.name AS contact_name, r.question, r.image_key, r.token,
                   r.status, r.answer, r.answered_at, r.expires_at, r.created_at
            FROM helper.requests r JOIN helper.contacts c ON c.id = r.contact_id
            """;

    private static final RowMapper<HelperRequest> ROW = RequestRepository::map;

    private final JdbcClient jdbc;

    public RequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(HelperRequest r) {
        jdbc.sql("""
                INSERT INTO helper.requests (id, user_id, contact_id, question, image_key, token, status, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?)""")
                .params(r.id(), r.userId(), r.contactId(), r.question(), r.imageKey(), r.token(),
                        Timestamp.from(r.expiresAt()), Timestamp.from(r.createdAt()))
                .update();
    }

    public void delete(String id) {
        jdbc.sql("DELETE FROM helper.requests WHERE id = ?").param(id).update();
    }

    public Optional<HelperRequest> find(UUID userId, String requestId) {
        return jdbc.sql(SELECT + "WHERE r.user_id = ? AND r.id = ?").param(userId).param(requestId).query(ROW).optional();
    }

    public Optional<HelperRequest> findByToken(String token) {
        return jdbc.sql(SELECT + "WHERE r.token = ?").param(token).query(ROW).optional();
    }

    public int countSince(UUID userId, Instant since) {
        return jdbc.sql("SELECT count(*) FROM helper.requests WHERE user_id = ? AND created_at >= ?")
                .param(userId)
                .param(Timestamp.from(since))
                .query(Integer.class)
                .single();
    }

    /** 아직 pending이고 만료 전일 때만 답을 저장한다. 저장했으면 true. */
    public boolean answer(String token, String answer, Instant now) {
        return jdbc.sql("""
                UPDATE helper.requests SET status = 'answered', answer = ?, answered_at = ?, updated_at = now()
                WHERE token = ? AND status = 'pending' AND expires_at > ?""")
                .params(answer, Timestamp.from(now), token, Timestamp.from(now))
                .update() == 1;
    }

    /** 만료 시각이 지난 pending을 expired로 바꾼다. 바꾼 개수. */
    public int expireDue(Instant now) {
        return jdbc.sql("UPDATE helper.requests SET status = 'expired', updated_at = now() WHERE status = 'pending' AND expires_at <= ?")
                .param(Timestamp.from(now))
                .update();
    }

    /** 사진을 지워야 하는 요청: 답을 받았거나 만료됐는데 사진 키가 남은 것. */
    public List<String[]> photosToDelete(int limit) {
        return jdbc.sql("SELECT id, image_key FROM helper.requests WHERE status <> 'pending' AND image_key IS NOT NULL LIMIT ?")
                .param(limit)
                .query((rs, i) -> new String[] {rs.getString(1), rs.getString(2)})
                .list();
    }

    /** 이 연락처의 요청 중 사진이 남은 것(연락처를 지우기 전에 사진부터 지우려고). */
    public List<String> imageKeysOfContact(UUID userId, String contactId) {
        return jdbc.sql("SELECT image_key FROM helper.requests WHERE user_id = ? AND contact_id = ? AND image_key IS NOT NULL")
                .param(userId)
                .param(contactId)
                .query(String.class)
                .list();
    }

    public void clearImageKey(String requestId) {
        jdbc.sql("UPDATE helper.requests SET image_key = NULL, updated_at = now() WHERE id = ?").param(requestId).update();
    }

    private static HelperRequest map(ResultSet rs, int rowNum) throws SQLException {
        return new HelperRequest(
                rs.getString("id"),
                rs.getObject("user_id", UUID.class),
                rs.getString("contact_id"),
                rs.getString("contact_name"),
                rs.getString("question"),
                rs.getString("image_key"),
                rs.getString("token"),
                HelperRequest.Status.valueOf(rs.getString("status")),
                rs.getString("answer"),
                instant(rs, "answered_at"),
                instant(rs, "expires_at"),
                instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
