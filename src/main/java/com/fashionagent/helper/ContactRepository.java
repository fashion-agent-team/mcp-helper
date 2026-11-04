package com.fashionagent.helper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** helper.contacts. 다른 스키마는 읽지 않는다(약속 문서 7절). */
@Repository
public class ContactRepository {

    private final JdbcClient jdbc;

    public ContactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Contact> findByUser(UUID userId) {
        return jdbc.sql("""
                SELECT id, user_id, name, phone, relation FROM helper.contacts
                WHERE user_id = ? ORDER BY created_at, id""")
                .param(userId)
                .query(Contact.class)
                .list();
    }

    public Optional<Contact> find(UUID userId, String contactId) {
        return jdbc.sql("SELECT id, user_id, name, phone, relation FROM helper.contacts WHERE user_id = ? AND id = ?")
                .param(userId)
                .param(contactId)
                .query(Contact.class)
                .optional();
    }

    public int count(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM helper.contacts WHERE user_id = ?").param(userId).query(Integer.class).single();
    }

    public boolean existsPhone(UUID userId, String phone) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM helper.contacts WHERE user_id = ? AND phone = ?)")
                .param(userId)
                .param(phone)
                .query(Boolean.class)
                .single();
    }

    public void insert(Contact c) {
        jdbc.sql("INSERT INTO helper.contacts (id, user_id, name, phone, relation) VALUES (?, ?, ?, ?, ?)")
                .params(c.id(), c.userId(), c.name(), c.phone(), c.relation())
                .update();
    }

    /** 요청 기록은 ON DELETE CASCADE로 같이 지워진다. 사진은 부르는 쪽이 먼저 지운다. */
    public boolean delete(UUID userId, String contactId) {
        return jdbc.sql("DELETE FROM helper.contacts WHERE user_id = ? AND id = ?")
                .param(userId)
                .param(contactId)
                .update() > 0;
    }
}
