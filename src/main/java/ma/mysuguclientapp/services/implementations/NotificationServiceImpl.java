package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.CampagneNotificationRequestDTO;
import ma.mysuguclientapp.dtos.CampagneNotificationResultDTO;
import ma.mysuguclientapp.dtos.NotificationDTO;
import ma.mysuguclientapp.entities.Notification;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.TypeNotification;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.exceptions.UnauthorizedException;
import ma.mysuguclientapp.repositories.NotificationRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.FcmService;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final FcmService fcmService;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDTO> getMesNotifications(String accessToken, Pageable pageable) {
        User user = getUserFromToken(accessToken);
        return notificationRepository.findByDestinataireIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> getMesNotificationsNonLues(String accessToken) {
        User user = getUserFromToken(accessToken);
        return notificationRepository.findByDestinataireIdAndLueFalseOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getNombreNonLues(String accessToken) {
        User user = getUserFromToken(accessToken);
        return notificationRepository.countByDestinataireIdAndLueFalse(user.getId());
    }

    @Override
    public NotificationDTO marquerCommeLue(String accessToken, Long notificationId) {
        User user = getUserFromToken(accessToken);
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification non trouvée"));

        if (!notif.getDestinataire().getId().equals(user.getId())) {
            throw new UnauthorizedException("Cette notification ne vous appartient pas");
        }

        if (!notif.getLue()) {
            notif.setLue(true);
            notif.setLueAt(LocalDateTime.now());
            notif = notificationRepository.save(notif);
        }
        return toDTO(notif);
    }

    @Override
    public void marquerToutesCommeLues(String accessToken) {
        User user = getUserFromToken(accessToken);
        notificationRepository.marquerToutesCommeVues(user.getId(), LocalDateTime.now());
    }

    @Override
    public void envoyerNotification(Long userId, String titre, String message,
                                    TypeNotification type, Long entityId, String entityType) {
        userRepository.findById(userId).ifPresent(user -> {
            Notification notif = Notification.builder()
                    .destinataire(user)
                    .titre(titre)
                    .message(message)
                    .type(type)
                    .entityId(entityId)
                    .entityType(entityType)
                    .lue(false)
                    .build();
            notificationRepository.save(notif);
            log.info("Notification '{}' envoyée à l'utilisateur {}", type, userId);

            // Envoi de la notification push FCM
            Map<String, String> data = buildFcmData(type, entityId, entityType);
            fcmService.sendToUser(userId, titre, message, data);
        });
    }

    @Override
    public void envoyerNotificationCommande(Long userId, String numeroCommande,
                                             TypeNotification type, Long commandeId) {
        String titre = buildTitreCommande(type);
        String message = buildMessageCommande(type, numeroCommande);
        envoyerNotification(userId, titre, message, type, commandeId, "COMMANDE");
    }

    @Override
    public void envoyerNotificationSysteme(Long userId, String titre, String message) {
        envoyerNotification(userId, titre, message, TypeNotification.SYSTEME, null, null);
    }

    @Override
    public CampagneNotificationResultDTO envoyerCampagne(CampagneNotificationRequestDTO request) {
        TypeNotification type = parseTypeCampagne(request.getType());

        // Ciblage utilisateur unique si destinataireUserId est renseigné
        List<User> destinataires;
        if (request.getDestinataireUserId() != null) {
            User user = userRepository.findById(request.getDestinataireUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Utilisateur introuvable : " + request.getDestinataireUserId()));
            destinataires = List.of(user);
        } else {
            destinataires = resolverDestinataires(request.getCibleRole());
        }

        if (destinataires.isEmpty()) {
            log.warn("Campagne '{}' : aucun destinataire actif trouvé pour le rôle '{}'",
                    request.getTitre(), request.getCibleRole());
            return CampagneNotificationResultDTO.builder()
                    .destinatairesCount(0).notificationsCreees(0).pushEnvoyees(0)
                    .envoyeeAt(LocalDateTime.now()).build();
        }

        // Sauvegarde in-app pour chaque destinataire
        List<Notification> notifs = destinataires.stream()
                .map(user -> Notification.builder()
                        .destinataire(user)
                        .titre(request.getTitre())
                        .message(request.getMessage())
                        .type(type)
                        .entityId(request.getEntityId())
                        .entityType(request.getEntityType())
                        .lue(false)
                        .build())
                .toList();
        notificationRepository.saveAll(notifs);

        // Push FCM en batch
        List<Long> userIds = destinataires.stream().map(User::getId).toList();
        Map<String, String> data = buildFcmData(type, request.getEntityId(), request.getEntityType());
        fcmService.sendToUsers(userIds, request.getTitre(), request.getMessage(), data);

        log.info("Campagne '{}' envoyée à {} destinataires (rôle: {})",
                request.getTitre(), destinataires.size(), request.getCibleRole());

        return CampagneNotificationResultDTO.builder()
                .destinatairesCount(destinataires.size())
                .notificationsCreees(notifs.size())
                .pushEnvoyees(userIds.size())
                .envoyeeAt(LocalDateTime.now())
                .build();
    }

    private TypeNotification parseTypeCampagne(String type) {
        if (type == null) return TypeNotification.PROMOTION;
        return switch (type.toUpperCase()) {
            case "SYSTEME" -> TypeNotification.SYSTEME;
            case "PROMOTION" -> TypeNotification.PROMOTION;
            default -> throw new BadRequestException(
                    "Type de campagne invalide : '" + type + "'. Valeurs acceptées : PROMOTION, SYSTEME");
        };
    }

    private List<User> resolverDestinataires(String cibleRole) {
        if (cibleRole == null || cibleRole.isBlank() || "ALL".equalsIgnoreCase(cibleRole)) {
            // Tous les utilisateurs actifs (toutes rôles confondus)
            return userRepository.findAll().stream()
                    .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                    .toList();
        }
        UserRole role;
        try {
            role = UserRole.valueOf(cibleRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Rôle cible invalide : '" + cibleRole + "'. Valeurs acceptées : CLIENT, LIVREUR, RESTAURANT_OWNER, ADMIN, ALL");
        }
        return userRepository.findByRoleAndIsActive(role, true);
    }

    private String buildTitreCommande(TypeNotification type) {
        return switch (type) {
            case COMMANDE_CONFIRMEE -> "Commande confirmée";
            case COMMANDE_EN_PREPARATION -> "Commande en préparation";
            case COMMANDE_PRETE -> "Commande prête";
            case COMMANDE_EN_COURS -> "En cours de livraison";
            case COMMANDE_LIVREE -> "Commande livrée";
            case COMMANDE_ANNULEE -> "Commande annulée";
            case LIVREUR_ASSIGNE -> "Livreur assigné";
            default -> "Mise à jour de votre commande";
        };
    }

    private String buildMessageCommande(TypeNotification type, String num) {
        return switch (type) {
            case COMMANDE_CONFIRMEE -> "Votre commande " + num + " a été confirmée par le restaurant.";
            case COMMANDE_EN_PREPARATION -> "Le restaurant prépare votre commande " + num + ".";
            case COMMANDE_PRETE -> "Votre commande " + num + " est prête !";
            case COMMANDE_EN_COURS -> "Votre commande " + num + " est en cours de livraison.";
            case COMMANDE_LIVREE -> "Votre commande " + num + " a été livrée. Bon appétit !";
            case COMMANDE_ANNULEE -> "Votre commande " + num + " a été annulée.";
            case LIVREUR_ASSIGNE -> "Une nouvelle livraison vous a été assignée : commande " + num + ".";
            default -> "Mise à jour de votre commande " + num + ".";
        };
    }

    private Map<String, String> buildFcmData(TypeNotification type, Long entityId, String entityType) {
        Map<String, String> data = new java.util.HashMap<>();
        data.put("type", type.name());
        if (type == TypeNotification.MESSAGE) {
            data.put("channelId", "mysuku_customer_messages_v1");
            data.put("sound", "message_sound");
            data.put("badge", "1");
        }
        if (entityId != null) data.put("entityId", entityId.toString());
        if (entityType != null) data.put("entityType", entityType);
        return data;
    }

    private User getUserFromToken(String bearerToken) {
        String jwt = bearerToken != null && bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7) : bearerToken;
        String email = jwtTokenProvider.getEmailFromToken(jwt);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsParEntite(Long entityId, String entityType) {
        return notificationRepository
                .findByEntityIdAndEntityTypeOrderByCreatedAtDesc(entityId, entityType)
                .stream().map(this::toDTOWithUser).collect(Collectors.toList());
    }

    private NotificationDTO toDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .destinataireId(n.getDestinataire().getId())
                .titre(n.getTitre())
                .message(n.getMessage())
                .type(n.getType().name())
                .lue(n.getLue())
                .lueAt(n.getLueAt())
                .entityId(n.getEntityId())
                .entityType(n.getEntityType())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private NotificationDTO toDTOWithUser(Notification n) {
        User u = n.getDestinataire();
        return NotificationDTO.builder()
                .id(n.getId())
                .destinataireId(u.getId())
                .destinataireNom(u.getNom())
                .destinatairePrenom(u.getPrenom())
                .destinataireEmail(u.getEmail())
                .titre(n.getTitre())
                .message(n.getMessage())
                .type(n.getType().name())
                .lue(n.getLue())
                .lueAt(n.getLueAt())
                .entityId(n.getEntityId())
                .entityType(n.getEntityType())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
