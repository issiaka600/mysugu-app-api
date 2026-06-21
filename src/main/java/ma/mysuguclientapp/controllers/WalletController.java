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
    public ResponseEntity<WalletDTO> getMonWallet(@AuthenticationPrincipal String email) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    @PostMapping("/recharger")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<WalletDTO> recharger(@AuthenticationPrincipal String email,
                                                @RequestBody RechargeWalletDTO dto) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(walletService.recharger(userId, dto));
    }

    @PostMapping("/payer")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<WalletDTO> payer(@AuthenticationPrincipal String email,
                                            @RequestBody PaiementWalletDTO dto) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(walletService.payer(userId, dto));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<Page<TransactionWalletDTO>> getHistorique(
            @AuthenticationPrincipal String email,
            Pageable pageable) {
        Long userId = getUserId(email);
        return ResponseEntity.ok(walletService.getHistoriqueTransactions(userId, pageable));
    }

    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WalletDTO> getWalletAdmin(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    @GetMapping("/admin/{userId}/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<TransactionWalletDTO>> getHistoriqueAdmin(
            @PathVariable Long userId, Pageable pageable) {
        return ResponseEntity.ok(walletService.getHistoriqueTransactions(userId, pageable));
    }

    private Long getUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }
}
