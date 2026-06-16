package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.commerce.*;
import ma.mysuguclientapp.services.interfaces.CodePromoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/codes-promo")
@RequiredArgsConstructor
public class CodePromoController {

    private final CodePromoService codePromoService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CodePromoDTO> creerCodePromo(@RequestBody CodePromoCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(codePromoService.creerCodePromo(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CodePromoDTO> getCodePromo(@PathVariable Long id) {
        return ResponseEntity.ok(codePromoService.getCodePromo(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CodePromoDTO>> getAllCodesPromo() {
        return ResponseEntity.ok(codePromoService.getAllCodesPromo());
    }

    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<List<CodePromoDTO>> getCodesPromoDisponibles() {
        return ResponseEntity.ok(codePromoService.getCodesPromoActifs());
    }

    @PatchMapping("/{id}/activer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CodePromoDTO> activerDesactiver(@PathVariable Long id,
                                                           @RequestParam boolean actif) {
        return ResponseEntity.ok(codePromoService.activerDesactiver(id, actif));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimerCodePromo(@PathVariable Long id) {
        codePromoService.supprimerCodePromo(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/valider")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<ResultatCodePromoDTO> validerCodePromo(
            @RequestBody AppliquerCodePromoDTO dto,
            @AuthenticationPrincipal String email) {
        // userId will be resolved from security context in service
        return ResponseEntity.ok(codePromoService.validerEtCalculer(dto, null));
    }
}
