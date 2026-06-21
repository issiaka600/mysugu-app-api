package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.cart.AjouterItemDTO;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.PanierServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/panier")
@RequiredArgsConstructor
public class PanierController {

    private final PanierServiceImpl panierService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, Object>> getMonPanier(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(panierService.getPanier(getUserId(email)));
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, Object>> ajouterItem(@AuthenticationPrincipal String email,
                                                           @RequestBody AjouterItemDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(panierService.ajouterItem(getUserId(email), dto));
    }

    @PatchMapping("/items/{itemId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Map<String, Object>> modifierQuantite(@AuthenticationPrincipal String email,
                                                                @PathVariable Long itemId,
                                                                @RequestParam Integer quantite) {
        return ResponseEntity.ok(panierService.modifierQuantite(getUserId(email), itemId, quantite));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> viderPanier(@AuthenticationPrincipal String email) {
        panierService.viderPanier(getUserId(email));
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
