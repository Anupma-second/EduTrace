package com.sdms.prototype.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Demo build: no email is sent and there is no outbox. Requests and notices are shown inside each
 * user's own profile (for a student: "Access requests" and "My records"). Calls are kept so a real
 * mail or push channel can be plugged in here later.
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    public void send(String to, String subject, String body, String kind) {
        log.debug("notification ({}) for {} - {}", kind, to, subject);
    }
}
