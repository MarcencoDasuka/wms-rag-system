package com.isd.wms.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Service responsible for sending email notifications.
 * <p>
 * Currently supports sending account verification emails using a Thymeleaf
 * HTML template. Email configuration (sender address, frontend URL) is
 * externalized via Spring properties.
 * </p>
 *
 * @see JavaMailSender
 * @see SpringTemplateEngine
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${wms.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${wms.mail.from:pajiloybaraban@gmail.com}")
    private String fromEmail;

    /**
     * Sends a verification email to a newly registered user.
     * <p>
     * The email contains a confirmation link that includes a unique token.
     * The link points to the frontend verification endpoint.
     * </p>
     *
     * @param toEmail the recipient email address
     * @param username the username of the recipient (displayed in the email)
     * @param token the unique verification token
     * @throws RuntimeException if sending the email fails
     */
    public void sendVerificationEmail(String toEmail, String username, String token) {
        try {
            Context ctx = new Context();
            ctx.setVariable("username", username);
            ctx.setVariable("email", toEmail);
            ctx.setVariable("confirmUrl", frontendUrl + "/verify?token=" + token);

            String html = templateEngine.process("email/operator-welcome", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "ISD Warehouse System");
            helper.setTo(toEmail);
            helper.setSubject("Account verification — ISD Warehouse");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send verification email to {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email");
        }
    }
}
