package ma.mysuguclientapp.legacy.deliveryman.controller;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.Avis;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutAvis;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.legacy.deliveryman.dto.MessageResponse;
import ma.mysuguclientapp.repositories.AvisRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Avis du livreur (contrat 6valley §5.11–§5.12). `is_saved` DOIT être un booléen JSON (fragile).
 */
@RestController
@RequestMapping("/api/v2/delivery-man")
@RequiredArgsConstructor
public class DeliveryManReviewController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final AvisRepository avisRepository;

    @GetMapping("/review-list")
    public Map<String, Object> reviewList(@AuthenticationPrincipal String email,
                                          @RequestParam(name = "is_saved", required = false) String isSaved,
                                          @RequestParam(defaultValue = "20") int limit,
                                          @RequestParam(defaultValue = "1") int offset) {
        User l = livreur(email);
        List<Avis> all = "1".equals(isSaved)
                ? avisRepository.findByLivreurIdAndSauvegardeOrderByCreatedAtDesc(l.getId(), true)
                : avisRepository.findByLivreurIdOrderByCreatedAtDesc(l.getId());
        int from = Math.max(0, (offset - 1) * limit);
        List<Avis> pageItems = from >= all.size() ? List.of() : all.subList(from, Math.min(all.size(), from + limit));
        List<Map<String, Object>> review = pageItems.stream().map(this::toReviewMap).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_size", all.size());
        m.put("limit", String.valueOf(limit));
        m.put("offset", String.valueOf(offset));
        m.put("review", review);
        return m;
    }

    @PostMapping("/save-review")
    public ResponseEntity<?> saveReview(@AuthenticationPrincipal String email, @RequestBody Map<String, Object> body) {
        livreur(email);
        Object rid = body.get("review_id");
        if (rid == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("errors", List.of(Map.of("code", "review", "message", "Avis introuvable."))));
        }
        Avis a = avisRepository.findById(Long.valueOf(rid.toString())).orElse(null);
        if (a == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("errors", List.of(Map.of("code", "review", "message", "Avis introuvable."))));
        }
        a.setSauvegarde("1".equals(String.valueOf(body.get("is_saved"))) || Boolean.TRUE.equals(body.get("is_saved")));
        avisRepository.save(a);
        return ResponseEntity.ok(new MessageResponse("Avis mis à jour."));
    }

    private Map<String, Object> toReviewMap(Avis a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("product_id", 0);
        m.put("customer_id", a.getAuteur() != null ? a.getAuteur().getId() : 0);
        m.put("delivery_man_id", a.getLivreur() != null ? a.getLivreur().getId() : null);
        m.put("order_id", a.getCommande() != null ? a.getCommande().getId() : null);
        m.put("comment", a.getCommentaire());
        m.put("rating", a.getNoteLivreur() != null ? a.getNoteLivreur() : 0);
        m.put("status", a.getStatut() == StatutAvis.APPROUVE ? 1 : 0);
        m.put("is_saved", Boolean.TRUE.equals(a.getSauvegarde())); // fragile : booléen JSON
        m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().format(TS) : null);
        m.put("updated_at", a.getUpdatedAt() != null ? a.getUpdatedAt().format(TS) : null);
        Map<String, Object> customer = new LinkedHashMap<>();
        if (a.getAuteur() != null) {
            customer.put("id", a.getAuteur().getId());
            customer.put("f_name", nvl(a.getAuteur().getPrenom()));
            customer.put("l_name", nvl(a.getAuteur().getNom()));
            customer.put("image", nvl(a.getAuteur().getAvatar()));
        }
        m.put("customer", customer);
        return m;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private User livreur(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Livreur non authentifié"));
        if (u.getRole() != UserRole.LIVREUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux livreurs");
        }
        return u;
    }
}
