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
    private final ma.mysuguclientapp.config.AdminPasswordVault coffre;

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
        // Le compte est cree par un administrateur, pas par le proprietaire lui-meme : il n'y a
        // donc personne pour cliquer sur le lien de verification. Sans ce flag, UserServiceImpl.login
        // le rejette avec un 403 "Email non verifie" meme quand le mot de passe est correct.
        owner.setEmailVerified(true);

boolean sendInvite = Boolean.TRUE.equals(dto.getOwnerSendInvite());
if (!sendInvite && dto.getOwnerPassword() != null && !dto.getOwnerPassword().isEmpty()) {
            owner.setPassword(passwordEncoder.encode(dto.getOwnerPassword()));
            // Le backoffice a choisi ce mot de passe et doit pouvoir le relire ensuite pour
            // le communiquer au restaurant par téléphone : on en garde une copie chiffrée.
            owner.setMotDePasseAdmin(coffre.encrypt(dto.getOwnerPassword()));
        } else {
            // Mot de passe tiré au hasard : personne ne le connaît, il n'y a rien à conserver.
            owner.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            owner.setMotDePasseAdmin(null);
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
