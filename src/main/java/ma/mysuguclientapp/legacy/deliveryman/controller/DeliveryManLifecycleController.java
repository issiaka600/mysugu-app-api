package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.PreuveLivraison;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.MethodePaiement;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.TypeNotification;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.dto.ErrorsResponse;
import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.PreuveLivraisonRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.CaisseServiceImpl;
import ma.mysuguclientapp.services.implementations.GainsLivreurServiceImpl;
import ma.mysuguclientapp.services.implementations.MinioService;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cycle de vie de la livraison (contrat 6valley §4). update-order-status (avec effets LIVREE :
 * gains + caisse + libération livreur), expected-delivery, pause, payment-status, + vérification
 * de livraison (OTP + renvoi + photo de preuve). Ces routes arrivent en POST (methode reelle).
 */
@RestController
@RequestMapping("/api/v2/delivery-man")
@RequiredArgsConstructor
@Slf4j
public class DeliveryManLifecycleController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final GainsLivreurServiceImpl gainsLivreurService;
    private final CaisseServiceImpl caisseService;
    private final NotificationService notificationService;
    private final MinioService minioService;
    private final PreuveLivraisonRepository preuveLivraisonRepository;
    private final ma.mysuguclientapp.services.interfaces.FcmService fcmService;

    // ---------- T5 : lifecycle ----------

    @PostMapping("/update-order-status")
    @Transactional
    public ResponseEntity<?> updateOrderStatus(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        Commande c = owned(orderId(body), l);
        if (c.getStatut() == StatutCommande.LIVREE) {
            return ResponseEntity.ok(Map.of("success", 0, "message", "order is already delivered."));
        }
        String status = str(body.get("status"));
        String cause = str(body.get("cause"));
        if (status == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorsResponse.of("status", "Statut requis."));
        }
        switch (status.toLowerCase()) {
            case "delivered" -> marquerLivree(c);
            case "canceled", "returned" -> {
                c.setStatut(StatutCommande.ANNULEE);
                if (cause != null) c.setRaisonAnnulation(cause);
                libererLivreur(c);
                commandeRepository.save(c);
                notifier(c.getLivreur(), c, "Commande annulée", "La commande " + c.getNumeroCommande() + " a été annulée.");
            }
            case "out_for_delivery" -> {
                c.setStatut(StatutCommande.EN_COURS);
                commandeRepository.save(c);
                notifier(c.getClient(), c, "Commande en route", "Votre commande " + c.getNumeroCommande() + " est en cours de livraison.");
            }
            default -> {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorsResponse.of("status", "Statut non supporté."));
            }
        }
        return ResponseEntity.ok(new MessageResponse("Order status updated successfully!"));
    }

    /** Passage à LIVREE : effets argent (gains + caisse) + libération du livreur (techspec §7). */
    private void marquerLivree(Commande c) {
        c.setStatut(StatutCommande.LIVREE);
        c.setLivreeAt(LocalDateTime.now());
        c.setStatutPaiement(StatutPaiement.PAYE);
        libererLivreur(c);
        commandeRepository.save(c);
        if (c.getMethodePaiement() == MethodePaiement.ESPECES) {
            try {
                caisseService.enregistrerCollecteClient(c);
            } catch (Exception e) {
                log.warn("Collecte caisse commande {} : {}", c.getNumeroCommande(), e.getMessage());
            }
        }
        if (c.getLivreur() != null) {
            try {
                gainsLivreurService.enregistrerGains(c);
            } catch (Exception e) {
                log.warn("Gains livreur commande {} : {}", c.getNumeroCommande(), e.getMessage());
            }
        }
        notifier(c.getClient(), c, "Commande livrée", "Votre commande " + c.getNumeroCommande() + " a été livrée.");
    }

    @PostMapping("/update-expected-delivery")
    @Transactional
    public ResponseEntity<?> updateExpectedDelivery(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        Commande c = owned(orderId(body), l);
        if (c.getStatut() == StatutCommande.LIVREE) {
            return ResponseEntity.ok(Map.of("success", 0, "message", "order is already delivered."));
        }
        c.setCauseReport(str(body.get("cause")));
        String date = str(body.get("expected_delivery_date"));
        if (date != null) {
            try {
                c.setDateLivraisonPrevue(LocalDateTime.parse(date.replace(" ", "T")));
            } catch (Exception ignore) {
                // Format libre côté app : on conserve la cause, la date reste inchangée si non parsable.
            }
        }
        commandeRepository.save(c);
        return ResponseEntity.ok(new MessageResponse("Date de livraison mise à jour."));
    }

    @PostMapping("/order-update-is-pause")
    @Transactional
    public ResponseEntity<?> updateIsPause(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        Commande c = owned(orderId(body), l);
        if (c.getStatut() == StatutCommande.LIVREE) {
            return ResponseEntity.ok(Map.of("success", 0, "message", "order is already delivered."));
        }
        c.setEnPause("1".equals(str(body.get("is_pause"))) || Boolean.TRUE.equals(body.get("is_pause")));
        c.setCausePause(str(body.get("cause")));
        commandeRepository.save(c);
        return ResponseEntity.ok(new MessageResponse("Statut de pause mis à jour."));
    }

    @PostMapping("/update-payment-status")
    @Transactional
    public ResponseEntity<?> updatePaymentStatus(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        Commande c = owned(orderId(body), l);
        if ("paid".equalsIgnoreCase(str(body.get("payment_status")))) {
            c.setStatutPaiement(StatutPaiement.PAYE);
            commandeRepository.save(c);
        }
        return ResponseEntity.ok(new MessageResponse("Statut de paiement mis à jour."));
    }

    // ---------- T6 : vérification de livraison ----------

    @PostMapping("/verify-order-delivery-otp")
    @Transactional
    public ResponseEntity<?> verifyDeliveryOtp(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        Commande c = owned(orderId(body), l);
        String code = str(body.get("verification_code"));
        if (code != null && code.equals(c.getCodeVerificationLivraison())) {
            c.setLivraisonVerifiee(true);
            commandeRepository.save(c);
            return ResponseEntity.ok(new MessageResponse("Livraison vérifiée avec succès."));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Code de vérification incorrect."));
    }

    @PostMapping("/resend-verification-code")
    @Transactional
    public ResponseEntity<?> resendVerificationCode(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        User l = livreur(email);
        Commande c = owned(orderId(body), l);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        c.setCodeVerificationLivraison(code);
        commandeRepository.save(c);
        // Notif in-app + push FCM du code au client (best effort).
        String msg = "Votre code de vérification de livraison pour la commande "
                + c.getNumeroCommande() + " est : " + code;
        notifier(c.getClient(), c, "Code de livraison", msg);
        if (c.getClient() != null) {
            try {
                fcmService.sendToUser(c.getClient().getId(), "Code de livraison", msg,
                        java.util.Map.of("type", "delivery_otp", "order_id", String.valueOf(c.getId())));
            } catch (Exception e) {
                log.warn("Échec push FCM code livraison commande {}: {}", c.getNumeroCommande(), e.getMessage());
            }
        }
        return ResponseEntity.ok(new MessageResponse("Code de vérification renvoyé."));
    }

    @PostMapping(value = "/order-delivery-verification", consumes = {"multipart/form-data"})
    @Transactional
    public ResponseEntity<?> orderDeliveryVerification(@AuthenticationPrincipal String email,
                                                       @RequestParam("order_id") Long orderId,
                                                       @RequestParam(value = "image", required = false) MultipartFile[] images) {
        User l = livreur(email);
        Commande c = owned(orderId, l);
        if (images != null) {
            for (MultipartFile img : images) {
                if (img == null || img.isEmpty()) continue;
                try {
                    String url = minioService.uploadFile(img, "delivery-man/verification-image");
                    preuveLivraisonRepository.save(PreuveLivraison.builder()
                            .commande(c).imageUrl(url).build());
                } catch (Exception e) {
                    log.warn("Upload preuve livraison commande {} : {}", c.getNumeroCommande(), e.getMessage());
                }
            }
        }
        return ResponseEntity.ok(new MessageResponse("successfully_uploaded"));
    }

    // ---------- helpers ----------

    private void libererLivreur(Commande c) {
        if (c.getLivreur() != null) {
            c.getLivreur().setLivreurDisponible(true);
            userRepository.save(c.getLivreur());
        }
    }

    private void notifier(User dest, Commande c, String titre, String message) {
        if (dest == null) return;
        try {
            notificationService.envoyerNotification(dest.getId(), titre, message,
                    TypeNotification.COMMANDE_CONFIRMEE, c.getId(), "COMMANDE");
        } catch (Exception e) {
            log.warn("Notification commande {} : {}", c.getNumeroCommande(), e.getMessage());
        }
    }

    private Long orderId(Map<String, Object> body) {
        Object v = body.get("order_id");
        if (v == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "order_id requis");
        return Long.valueOf(v.toString());
    }

    private Commande owned(Long orderId, User livreur) {
        Commande c = commandeRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable."));
        if (c.getLivreur() == null || !c.getLivreur().getId().equals(livreur.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette commande ne vous est pas assignée.");
        }
        return c;
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
