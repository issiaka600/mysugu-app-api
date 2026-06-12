package ma.mysuguclientapp.services;

import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.services.implementations.AppleAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppleAuthServiceTest {

    private final AppleAuthService service = new AppleAuthService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appleClientId", "ma.mysuku.customer");
    }

    @Test
    void invalidToken_returnsBadRequest() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.verifyIdToken("test", "abc"));
        assertEquals("Le token Apple est invalide", ex.getMessage());
    }
}
