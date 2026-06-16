package ma.mysuguclientapp.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.MessageContactCreateDTO;
import ma.mysuguclientapp.dtos.MessageContactDTO;
import ma.mysuguclientapp.dtos.MessageContactReponseDTO;
import ma.mysuguclientapp.dtos.MessageContactStatsDTO;
import ma.mysuguclientapp.dtos.MessageContactStatutUpdateDTO;
import ma.mysuguclientapp.enumerations.StatutMessageContact;
import ma.mysuguclientapp.services.interfaces.MessageContactService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MessageContactController {

    private final MessageContactService service;

    // ---------- PUBLIC ----------

    @PostMapping("/api/contact")
    public ResponseEntity<MessageContactDTO> envoyer(
            @Valid @RequestBody MessageContactCreateDTO dto,
            HttpServletRequest request) {

        String ip = extractIp(request);
        String ua = request.getHeader("User-Agent");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.soumettre(dto, ip, ua));
    }

    // ---------- ADMIN ----------

    @GetMapping("/api/admin/contact-messages")
    public ResponseEntity<Page<MessageContactDTO>> liste(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "statut", required = false) StatutMessageContact statut,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(service.rechercher(q, statut, pageable));
    }

    @GetMapping("/api/admin/contact-messages/stats")
    public ResponseEntity<MessageContactStatsDTO> stats() {
        return ResponseEntity.ok(service.stats());
    }

    @GetMapping("/api/admin/contact-messages/{id}")
    public ResponseEntity<MessageContactDTO> detail(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PatchMapping("/api/admin/contact-messages/{id}/statut")
    public ResponseEntity<MessageContactDTO> updateStatut(
            @PathVariable Long id,
            @Valid @RequestBody MessageContactStatutUpdateDTO dto) {
        return ResponseEntity.ok(service.updateStatut(id, dto));
    }

    @PostMapping("/api/admin/contact-messages/{id}/reponse")
    public ResponseEntity<MessageContactDTO> repondre(
            @PathVariable Long id,
            @Valid @RequestBody MessageContactReponseDTO dto,
            @AuthenticationPrincipal String adminEmail) {
        if (adminEmail == null || adminEmail.isBlank()) {
            adminEmail = "admin";
        }
        return ResponseEntity.ok(service.repondre(id, dto, adminEmail));
    }

    @DeleteMapping("/api/admin/contact-messages/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    private String extractIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return request.getRemoteAddr();
    }
}
