package ma.mysuguclientapp.services.implementations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService service = new EmailService(mailSender);

    @BeforeEach
    void configureSender() {
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@mysugu.test");
    }

    @Test
    void otpRetourneSeulementQuandLeMailEstAccepte() {
        assertThatCode(() -> service.envoyerCodeOtp("client@example.com", "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void otpRetourneUneErreurClaireQuandSmtpEchoue() {
        doThrow(new MailSendException("SMTP indisponible"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.envoyerCodeOtp("client@example.com", "123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).contains("temporairement indisponible");
                });
    }
}
