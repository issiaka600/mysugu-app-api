package ma.mysuguclientapp;

import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.repositories.TokenVerificationRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.EmailService;
import ma.mysuguclientapp.services.interfaces.OwnerProvisioningService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnerProvisioningServiceTest {

    @Autowired OwnerProvisioningService provisioningService;
    @Autowired UserRepository userRepository;
    @Autowired TokenVerificationRepository tokenVerificationRepository;
    @Autowired TransactionTemplate tx;
    @MockitoBean EmailService emailService;

    private final String NEW_EMAIL = "owner-new-" + System.nanoTime() + "@test.mysugu";
    private final String EXISTING_OWNER_EMAIL = "owner-exist-" + System.nanoTime() + "@test.mysugu";
    private final String CLIENT_EMAIL = "client-" + System.nanoTime() + "@test.mysugu";

    @BeforeAll
    void setup() {
        User existing = new User();
        existing.setEmail(EXISTING_OWNER_EMAIL);
        existing.setNom("Exist"); existing.setPrenom("Owner");
        existing.setPassword("x"); existing.setRole(UserRole.RESTAURANT_OWNER);
        userRepository.save(existing);

        User client = new User();
        client.setEmail(CLIENT_EMAIL);
        client.setNom("Cli"); client.setPrenom("Ent");
        client.setPassword("x"); client.setRole(UserRole.CLIENT);
        userRepository.save(client);
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s ->
            userRepository.findByEmail(NEW_EMAIL).ifPresent(u -> {
                tokenVerificationRepository.findAll().stream()
                    .filter(t -> t.getUser() != null && u.getId().equals(t.getUser().getId()))
                    .forEach(tokenVerificationRepository::delete);
                userRepository.delete(u);
            }));
        tx.executeWithoutResult(s ->
            userRepository.findByEmail(EXISTING_OWNER_EMAIL).ifPresent(userRepository::delete));
        tx.executeWithoutResult(s ->
            userRepository.findByEmail(CLIENT_EMAIL).ifPresent(userRepository::delete));
    }

    @Test
    void creeUnNouveauRestaurateurAvecInvitation() {
        RestaurantCreateDTO dto = new RestaurantCreateDTO();
        dto.setOwnerEmail(NEW_EMAIL);
        dto.setOwnerNom("Nouveau"); dto.setOwnerPrenom("Resto");
        dto.setOwnerTel("0600000000");
        dto.setOwnerSendInvite(true);

        User owner = provisioningService.resolveOrCreateOwner(dto);

        assertThat(owner.getId()).isNotNull();
        assertThat(owner.getRole()).isEqualTo(UserRole.RESTAURANT_OWNER);
        assertThat(owner.getEmail()).isEqualTo(NEW_EMAIL);
    }

    @Test
    void reutiliseUnRestaurateurExistant() {
        RestaurantCreateDTO dto = new RestaurantCreateDTO();
        dto.setOwnerEmail(EXISTING_OWNER_EMAIL);
        dto.setOwnerSendInvite(true);

        User owner = provisioningService.resolveOrCreateOwner(dto);

        assertThat(owner.getEmail()).isEqualTo(EXISTING_OWNER_EMAIL);
    }

    @Test
    void rejetteUnEmailAvecUnAutreRole() {
        RestaurantCreateDTO dto = new RestaurantCreateDTO();
        dto.setOwnerEmail(CLIENT_EMAIL);

        assertThatThrownBy(() -> provisioningService.resolveOrCreateOwner(dto))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejetteQuandAucunOwnerFourni() {
        RestaurantCreateDTO dto = new RestaurantCreateDTO();
        assertThatThrownBy(() -> provisioningService.resolveOrCreateOwner(dto))
                .isInstanceOf(BadRequestException.class);
    }
}
