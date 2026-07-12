package ma.mysuguclientapp.services;

import ma.mysuguclientapp.dtos.commerce.CodePromoCreateDTO;
import ma.mysuguclientapp.dtos.commerce.CodePromoDTO;
import ma.mysuguclientapp.entities.CodePromo;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.TypeReduction;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.CodePromoRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.CodePromoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration pour l'ajout native de CodePromoService.mettreAJour (BUILD-MINIMAL,
 * plan 3h.1) et CodePromoRepository.findByCreatedById (scoping §3 de
 * docs/superpowers/specs/2026-07-10-vendor-3h-coupons-design.md).
 */
@SpringBootTest
class CodePromoUpdateTest {

    @Autowired CodePromoService codePromoService;
    @Autowired CodePromoRepository codePromoRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder encoder;

    private final String ownerAEmail = "cp-owner-a-" + System.nanoTime() + "@test.mysugu";
    private final String ownerBEmail = "cp-owner-b-" + System.nanoTime() + "@test.mysugu";
    private Long ownerAId, ownerBId, codeAId, codeBId, codeOtherId;

    @BeforeEach
    void seed() {
        ownerAId = newOwner(ownerAEmail);
        ownerBId = newOwner(ownerBEmail);
        codeAId = newCode("PROMOA" + System.nanoTime(), ownerAId);
        codeBId = newCode("PROMOB" + System.nanoTime(), ownerAId);
        codeOtherId = newCode("PROMOC" + System.nanoTime(), ownerBId);
    }

    @AfterEach
    void cleanup() {
        codePromoRepository.findById(codeAId).ifPresent(codePromoRepository::delete);
        codePromoRepository.findById(codeBId).ifPresent(codePromoRepository::delete);
        codePromoRepository.findById(codeOtherId).ifPresent(codePromoRepository::delete);
        userRepository.findById(ownerAId).ifPresent(userRepository::delete);
        userRepository.findById(ownerBId).ifPresent(userRepository::delete);
    }

    @Test
    void mettreAJour_updates_fields_and_preserves_usageCount_and_createdBy() {
        CodePromo before = codePromoRepository.findById(codeAId).orElseThrow();
        before.setUsageCount(3);
        codePromoRepository.save(before);

        CodePromoCreateDTO dto = new CodePromoCreateDTO();
        dto.setCode(before.getCode());
        dto.setDescription("Nouvelle description");
        dto.setTypeReduction(TypeReduction.MONTANT_FIXE);
        dto.setValeur(new BigDecimal("15.00"));
        dto.setMontantMinCommande(new BigDecimal("50.00"));
        dto.setMontantMaxReduction(new BigDecimal("20.00"));
        dto.setDateDebut(LocalDateTime.now().minusDays(1));
        dto.setDateFin(LocalDateTime.now().plusDays(10));
        dto.setUsageMax(100);

        CodePromoDTO updated = codePromoService.mettreAJour(codeAId, dto);

        assertThat(updated.getDescription()).isEqualTo("Nouvelle description");
        assertThat(updated.getValeur()).isEqualByComparingTo("15.00");
        assertThat(updated.getUsageMax()).isEqualTo(100);

        CodePromo persisted = codePromoRepository.findById(codeAId).orElseThrow();
        assertThat(persisted.getUsageCount()).isEqualTo(3);
        assertThat(persisted.getCreatedBy().getId()).isEqualTo(ownerAId);
    }

    @Test
    void mettreAJour_to_code_owned_by_another_row_conflicts() {
        CodePromo target = codePromoRepository.findById(codeAId).orElseThrow();
        CodePromo other = codePromoRepository.findById(codeBId).orElseThrow();

        CodePromoCreateDTO dto = new CodePromoCreateDTO();
        dto.setCode(other.getCode()); // collision with codeBId's code
        dto.setDescription("x");
        dto.setTypeReduction(TypeReduction.POURCENTAGE);
        dto.setValeur(BigDecimal.TEN);

        assertThatThrownBy(() -> codePromoService.mettreAJour(target.getId(), dto))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void mettreAJour_to_same_code_on_same_row_is_allowed() {
        CodePromo target = codePromoRepository.findById(codeAId).orElseThrow();

        CodePromoCreateDTO dto = new CodePromoCreateDTO();
        dto.setCode(target.getCode());
        dto.setDescription("inchangé sauf desc");
        dto.setTypeReduction(target.getTypeReduction());
        dto.setValeur(target.getValeur());

        CodePromoDTO updated = codePromoService.mettreAJour(target.getId(), dto);
        assertThat(updated.getCode()).isEqualTo(target.getCode());
    }

    @Test
    void findByCreatedById_returns_only_that_owners_coupons() {
        var ownerACoupons = codePromoRepository.findByCreatedById(ownerAId);
        var ownerBCoupons = codePromoRepository.findByCreatedById(ownerBId);

        assertThat(ownerACoupons).extracting(CodePromo::getId).containsExactlyInAnyOrder(codeAId, codeBId);
        assertThat(ownerBCoupons).extracting(CodePromo::getId).containsExactly(codeOtherId);
    }

    private Long newOwner(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(encoder.encode("demo1234"));
        u.setNom("N");
        u.setPrenom("P");
        u.setRole(UserRole.RESTAURANT_OWNER);
        u.setIsActive(true);
        return userRepository.save(u).getId();
    }

    private Long newCode(String code, Long ownerId) {
        CodePromo promo = CodePromo.builder()
                .code(code)
                .description("desc " + code)
                .typeReduction(TypeReduction.POURCENTAGE)
                .valeur(new BigDecimal("10.00"))
                .usageCount(0)
                .isActive(true)
                .createdBy(userRepository.findById(ownerId).orElseThrow())
                .build();
        return codePromoRepository.save(promo).getId();
    }
}
