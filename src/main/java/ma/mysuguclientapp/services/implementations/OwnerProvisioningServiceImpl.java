package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.entities.TokenVerification;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.TokenVerificationRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.OwnerProvisioningService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerProvisioningServiceImpl implements OwnerProvisioningService {

    private final UserRepository userRepository;
    private final TokenVerificationRepository tokenVerificationRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User resolveOrCreateOwner(RestaurantCreateDTO dto) {
        if (dto.getOwnerId() != null) {
            User owner = userRepository.findById(dto.getOwnerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Propriétaire non trouvé"));
            assertOwnerRole(owner);
            return owner;
        }

        String email = dto.getOwnerEmail() != null ? dto.getOwnerEmail().trim() : null;
        if (email == null || email.isEmpty()) {
            throw new BadRequestException("Un restaurateur (ownerId ou ownerEmail) est obligatoire");
        }

        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User owner = existing.get();
            assertOwnerRole(owner);
            return owner;
        }

        User owner = new User();
        owner.setEmail(email);
        owner.setNom(dto.getOwnerNom() != null ? dto.getOwnerNom() : "");
        owner.setPrenom(dto.getOwnerPrenom() != null ? dto.getOwnerPrenom() : "");
        owner.setTelephone(dto.getOwnerTel());
        owner.setRole(UserRole.RESTAURANT_OWNER);
        owner.setIsActive(true);

        boolean sendInvite = Boolean.TRUE.equals(dto.getOwnerSendInvite());
        if (!sendInvite && dto.getOwnerPassword() != null && !dto.getOwnerPassword().isEmpty()) {
            owner.setPassword(passwordEncoder.encode(dto.getOwnerPassword()));
        } else {
            owner.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        }

        User saved = userRepository.save(owner);

        if (sendInvite) {
            String token = UUID.randomUUID().toString();
            TokenVerification tv = TokenVerification.builder()
                    .user(saved)
                    .token(token)
                    .type("PASSWORD_RESET")
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();
            tokenVerificationRepository.save(tv);
            emailService.envoyerInvitationRestaurateur(saved.getEmail(),
                    saved.getPrenom() + " " + saved.getNom(), token);
            log.info("Invitation restaurateur envoyée à {}", saved.getEmail());
        }

        return saved;
    }

    private void assertOwnerRole(User owner) {
        if (owner.getRole() != UserRole.RESTAURANT_OWNER && owner.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("L'utilisateur doit avoir le rôle RESTAURANT_OWNER");
        }
    }
}
