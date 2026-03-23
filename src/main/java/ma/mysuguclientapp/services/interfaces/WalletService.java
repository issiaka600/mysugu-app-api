package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.commerce.PaiementWalletDTO;
import ma.mysuguclientapp.dtos.commerce.RechargeWalletDTO;
import ma.mysuguclientapp.dtos.commerce.TransactionWalletDTO;
import ma.mysuguclientapp.dtos.commerce.WalletDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletService {
    WalletDTO getWallet(Long userId);
    WalletDTO recharger(Long userId, RechargeWalletDTO dto);
    WalletDTO payer(Long userId, PaiementWalletDTO dto);
    WalletDTO rembourser(Long userId, Long commandeId, java.math.BigDecimal montant);
    Page<TransactionWalletDTO> getHistoriqueTransactions(Long userId, Pageable pageable);
}
