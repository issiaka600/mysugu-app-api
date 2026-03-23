package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.cart.GainsLivreurDTO;
import ma.mysuguclientapp.dtos.cart.GainsSummaryDTO;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.implementations.GainsLivreurServiceImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/livreurs")
@RequiredArgsConstructor
public class GainsLivreurController {

    private final GainsLivreurServiceImpl gainsService;
    private final UserRepository userRepository;

    @GetMapping("/gains")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<Page<GainsLivreurDTO>> getMesGains(@AuthenticationPrincipal UserDetails userDetails,
                                                              Pageable pageable) {
        Long livreurId = getUserId(userDetails);
        return ResponseEntity.ok(gainsService.getHistoriqueGains(livreurId, pageable));
    }

    @GetMapping("/gains/summary")
    @PreAuthorize("hasRole('LIVREUR')")
    public ResponseEntity<GainsSummaryDTO> getMesSummary(@AuthenticationPrincipal UserDetails userDetails) {
        Long livreurId = getUserId(userDetails);
        return ResponseEntity.ok(gainsService.getSummary(livreurId));
    }

    @GetMapping("/{livreurId}/gains")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<GainsLivreurDTO>> getGainsAdmin(@PathVariable Long livreurId, Pageable pageable) {
        return ResponseEntity.ok(gainsService.getHistoriqueGains(livreurId, pageable));
    }

    @GetMapping("/{livreurId}/gains/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GainsSummaryDTO> getSummaryAdmin(@PathVariable Long livreurId) {
        return ResponseEntity.ok(gainsService.getSummary(livreurId));
    }

    private Long getUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
