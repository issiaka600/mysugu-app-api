package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.cart.AjouterItemDTO;
import ma.mysuguclientapp.dtos.cart.PanierDTO;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.PanierServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/panier")
@RequiredArgsConstructor
public class PanierController {

    private final PanierServiceImpl panierService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<PanierDTO> getMonPanier(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(panierService.getPanier(getUserId(userDetails)));
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<PanierDTO> ajouterItem(@AuthenticationPrincipal UserDetails userDetails,
                                                  @RequestBody AjouterItemDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(panierService.ajouterItem(getUserId(userDetails), dto));
    }

    @PatchMapping("/items/{itemId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<PanierDTO> modifierQuantite(@AuthenticationPrincipal UserDetails userDetails,
                                                       @PathVariable Long itemId,
                                                       @RequestParam Integer quantite) {
        return ResponseEntity.ok(panierService.modifierQuantite(getUserId(userDetails), itemId, quantite));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> viderPanier(@AuthenticationPrincipal UserDetails userDetails) {
        panierService.viderPanier(getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
