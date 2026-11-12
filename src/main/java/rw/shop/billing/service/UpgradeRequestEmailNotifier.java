package rw.shop.billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UpgradeRequestEmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(UpgradeRequestEmailNotifier.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String recipient;

    public UpgradeRequestEmailNotifier(
            JavaMailSender mailSender,
            @Value("${app.billing.email-notifications-enabled:false}") boolean enabled,
            @Value("${MAIL_FROM:${spring.mail.username:}}") String from,
            @Value("${app.billing.support-email}") String recipient) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.recipient = recipient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendUpgradeRequest(UpgradeRequestSubmitted request) {
        if (!enabled) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(recipient);
        message.setReplyTo(request.email());
        message.setSubject("[Sukuru] Premium upgrade request pending");
        message.setText("""
                A new Premium upgrade request is waiting for payment verification.

                Request ID: %s
                User ID: %s
                Name: %s
                Email: %s
                Payment method: %s
                Payment reference: %s
                Requested at: %s

                Review the request in the Sukuru billing records before approving it.
                """.formatted(
                request.requestId(), request.userId(), request.fullName(), request.email(),
                request.paymentMethod(), request.paymentReference() == null ? "Not provided" : request.paymentReference(),
                request.requestedAt()));

        try {
            mailSender.send(message);
            log.info("Sent billing upgrade request notification requestId={}", request.requestId());
        } catch (MailException exception) {
            log.error("Could not send billing upgrade request notification requestId={}", request.requestId(), exception);
        }
    }
}
