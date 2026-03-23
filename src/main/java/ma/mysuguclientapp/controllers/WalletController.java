package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.commerce.PaiementWalletDTO;
import ma.mysuguclientapp.dtos.commerce.RechargeWalletDTO;
import ma.mysuguclientapp.dtos.commerce.TransactionWalletDTO;
import ma.mysuguclientapp.dtos.commerce.WalletDTO;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.WalletService;
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
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<WalletDTO> getMonWallet(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    @PostMapping("/recharger")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<WalletDTO> recharger(@AuthenticationPrincipal UserDetails userDetails,
                                                @RequestBody RechargeWalletDTO dto) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(walletService.recharger(userId, dto));
    }

    @PostMapping("/payer")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<WalletDTO> payer(@AuthenticationPrincipal UserDetails userDetails,
                                            @RequestBody PaiementWalletDTO dto) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(walletService.payer(userId, dto));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<Page<TransactionWalletDTO>> getHistorique(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        Long userId = getUserId(userDetails);
        return ResponseEntity.ok(walletService.getHistoriqueTransactions(userId, pageable));
    }

    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WalletDTO> getWalletAdmin(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    private Long getUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
