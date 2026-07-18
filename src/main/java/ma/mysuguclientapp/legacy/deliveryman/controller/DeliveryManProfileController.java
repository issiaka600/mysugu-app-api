package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.ContactUrgence;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import ma.mysuguclientapp.legacy.deliveryman.service.DeliveryManInfoService;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.ContactUrgenceRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.interfaces.DeviceTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Profil / compte du livreur (contrat 6valley §2 + auth §1.5/§1.6).
 * Toutes ces routes sont authentifiées LIVREUR (SecurityConfig `/api/v2/delivery-man/**`).
 * Les "PUT*" arrivent en POST avec `_method:put` dans le corps : on les mappe en @PostMapping.
 */
@RestController
@RequestMapping("/api/v2/delivery-man")
@RequiredArgsConstructor
@Slf4j
public class DeliveryManProfileController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final DeliveryManInfoService infoService;
    private final DeviceTokenService deviceTokenService;
    private final MinioService minioService;
    private final ContactUrgenceRepository contactUrgenceRepository;
    private final CommandeRepository commandeRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/info")
    public Map<String, Object> info(@AuthenticationPrincipal String email) {
        return infoService.buildInfo(livreur(email));
    }

    @GetMapping("/profile-dashboard-counts")
    public Map<String, Object> dashboardCounts(@AuthenticationPrincipal String email) {
        User l = livreur(email);
        Map<String, Object> m = infoService.buildInfo(l);
        m.put("total_delivery_count", m.get("total_delivery"));
        m.put("delivered_orders", m.get("completed_delivery"));
        return m;
    }

    @PostMapping("/bank-info")
    public MessageResponse bankInfo(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        l.setBankName(str(body.get("bank_name")));
        l.setBranch(str(body.get("branch")));
        l.setAccountNo(str(body.get("account_no")));
        l.setHolderName(str(body.get("holder_name")));
        userRepository.save(l);
        return new MessageResponse("Informations bancaires mises à jour.");
    }

    /** PUT* is-online. Bloque le passage hors-ligne avec une livraison en cours (EN_COURS). */
    @PostMapping("/is-online")
    public ResponseEntity<?> isOnline(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        boolean online = "1".equals(str(body.get("is_online"))) || Boolean.TRUE.equals(body.get("is_online"));
        if (!online && commandeRepository.countByLivreurIdAndStatut(l.getId(), StatutCommande.EN_COURS) > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new MessageResponse("Vous avez une livraison en cours, impossible de passer hors-ligne."));
        }
        l.setLivreurDisponible(online);
        userRepository.save(l);
        return ResponseEntity.ok(new MessageResponse(online ? "Vous êtes en ligne." : "Vous êtes hors-ligne."));
    }

    @PostMapping("/change-status")
    public MessageResponse changeStatus(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        // 6valley "change-status" bascule la DISPONIBILITÉ (en ligne/hors-ligne), PAS l'activation
        // du compte. Anciennement setIsActive(...) : un appel sans champ "status" (status=null)
        // désactivait le compte livreur et bloquait TOUT login/appel (auth-002). On mappe donc sur
        // livreurDisponible et on ne touche JAMAIS isActive ici.
        String s = str(body.get("status"));
        boolean online = "1".equals(s) || Boolean.TRUE.equals(body.get("status"));
        l.setLivreurDisponible(online);
        userRepository.save(l);
        return new MessageResponse("Status changed successfully");
    }

    /** PUT* language-change. */
    @PostMapping("/language-change")
    public MessageResponse languageChange(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        l.setAppLanguage(str(body.get("current_language")));
        userRepository.save(l);
        return new MessageResponse("Langue mise à jour.");
    }

    /** PUT* update-fcm-token. `fcm_token == "no"` (logout) désactive les tokens du livreur. */
    @PostMapping("/update-fcm-token")
    public MessageResponse updateFcmToken(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        String token = str(body.get("fcm_token"));
        if (token == null || token.isBlank() || "no".equalsIgnoreCase(token)) {
            deviceTokenService.deactivateAllTokensForUser(l.getId());
        } else {
            deviceTokenService.registerToken(l.getId(), token, "android");
        }
        return new MessageResponse("FCM token mis à jour.");
    }

    /** PUT* (multipart) update-info : nom/adresse/mot de passe + image optionnelle. */
    @PostMapping(value = "/update-info", consumes = {"multipart/form-data"})
    public ResponseEntity<?> updateInfo(@AuthenticationPrincipal String email,
                                        @RequestParam(value = "f_name", required = false) String fName,
                                        @RequestParam(value = "l_name", required = false) String lName,
                                        @RequestParam(value = "address", required = false) String address,
                                        @RequestParam(value = "password", required = false) String password,
                                        @RequestParam(value = "confirm_password", required = false) String confirmPassword,
                                        @RequestPart(value = "image", required = false) MultipartFile image) {
        User l = livreur(email);
        if (fName != null && !fName.isBlank()) l.setPrenom(fName);
        if (lName != null && !lName.isBlank()) l.setNom(lName);
        if (address != null && l.getLocalisation() != null) l.getLocalisation().setAdresse(address);
        if (password != null && !password.isBlank()) {
            if (password.length() < 8 || !password.equals(confirmPassword)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("Mot de passe invalide ou non confirmé (min 8)."));
            }
            l.setPassword(passwordEncoder.encode(password));
        }
        if (image != null && !image.isEmpty()) {
            try {
                l.setAvatar(minioService.uploadFile(image, "delivery-man"));
            } catch (Exception e) {
                log.warn("Échec upload image profil livreur {}: {}", l.getId(), e.getMessage());
            }
        }
        userRepository.save(l);
        return ResponseEntity.ok(new MessageResponse("Profil mis à jour."));
    }

    @GetMapping("/emergency-contact-list")
    public Map<String, Object> emergencyContacts(@AuthenticationPrincipal String email) {
        livreur(email); // garde l'accès LIVREUR
        List<Map<String, Object>> list = contactUrgenceRepository.findByActifTrue().stream()
                .map(this::toContactMap)
                .toList();
        return Map.of("contact_list", list);
    }

    private Map<String, Object> toContactMap(ContactUrgence c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("user_id", c.getRestaurant() != null ? c.getRestaurant().getId() : 0);
        m.put("name", c.getNom());
        m.put("phone", c.getTelephone());
        m.put("country_code", "");
        m.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().format(TS) : null);
        m.put("updated_at", c.getCreatedAt() != null ? c.getCreatedAt().format(TS) : null);
        return m;
    }

    private User livreur(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Livreur non authentifié"));
        if (u.getRole() != UserRole.LIVREUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux livreurs");
        }
        return u;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
