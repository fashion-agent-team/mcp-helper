package com.fashionagent.helper;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 ↔ 게이트웨이 API의 /api/v1/me/contacts (openapi.yaml). 게이트웨이가 JWT를 확인하고 X-User-Id를 붙여 넘긴다.
 * 응답에 전화번호는 넣지 않는다.
 */
@RestController
@RequestMapping("/api/v1/me/contacts")
public class ContactController {

    private final HelperService service;

    public ContactController(HelperService service) {
        this.service = service;
    }

    public record NewContact(String name, String phone, String relation) {
    }

    @GetMapping
    public Map<String, List<Contact.View>> list(@RequestHeader(name = "X-User-Id", required = false) String userId) {
        return Map.of("contacts", service.listContacts(user(userId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Contact.View add(@RequestHeader(name = "X-User-Id", required = false) String userId, @RequestBody NewContact body) {
        return service.addContact(user(userId), body.name(), body.phone(), body.relation());
    }

    @DeleteMapping("/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(name = "X-User-Id", required = false) String userId, @PathVariable String contactId) {
        service.deleteContact(user(userId), contactId);
    }

    private static java.util.UUID user(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new HelperException(HttpStatus.UNAUTHORIZED, "unauthorized", "X-User-Id가 없어요(게이트웨이를 거쳐야 해요).");
        }
        return Ids.userId(userId);
    }
}
