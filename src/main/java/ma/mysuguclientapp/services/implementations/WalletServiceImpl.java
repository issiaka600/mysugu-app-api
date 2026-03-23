package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.commerce.PaiementWalletDTO;
import ma.mysuguclientapp.dtos.commerce.RechargeWalletDTO;
import ma.mysuguclientapp.dtos.commerce.TransactionWalletDTO;
import ma.mysuguclientapp.dtos.commerce.WalletDTO;
import ma.mysuguclientapp.entities.TransactionWallet;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.Wallet;
import ma.mysuguclientapp.enumerations.TypeTransaction;
import ma.mysuguclientapp.repositories.TransactionWalletRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.repositories.WalletRepository;
import ma.mysuguclientapp.services.interfaces.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final TransactionWalletRepository transactionWalletRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public WalletDTO getWallet(Long userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return toDTO(wallet);
    }

    @Override
    @Transactional
    public WalletDTO recharger(Long userId, RechargeWalletDTO dto) {
        if (dto.getMontant() == null || dto.getMontant().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant doit être positif");
        }
        Wallet wallet = getOrCreateWallet(userId);
        BigDecimal soldeAvant = wallet.getSolde();
        wallet.setSolde(soldeAvant.add(dto.getMontant()));
        walletRepository.save(wallet);

        enregistrerTransaction(wallet, TypeTransaction.RECHARGE, dto.getMontant(), soldeAvant,
                wallet.getSolde(), dto.getDescription() != null ? dto.getDescription() : "Recharge wallet",
                dto.getReferenceExterne());

        return toDTO(wallet);
    }

    @Override
    @Transactional
    public WalletDTO payer(Long userId, PaiementWalletDTO dto) {
        Wallet wallet = getOrCreateWallet(userId);
        if (wallet.getSolde().compareTo(dto.getMontant()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solde insuffisant");
        }
        BigDecimal soldeAvant = wallet.getSolde();
        wallet.setSolde(soldeAvant.subtract(dto.getMontant()));
        walletRepository.save(wallet);

        String ref = dto.getCommandeId() != null ? "CMD-" + dto.getCommandeId() : null;
        enregistrerTransaction(wallet, TypeTransaction.DEBIT, dto.getMontant(), soldeAvant,
                wallet.getSolde(), dto.getDescription() != null ? dto.getDescription() : "Paiement commande", ref);

        return toDTO(wallet);
    }

    @Override
    @Transactional
    public WalletDTO rembourser(Long userId, Long commandeId, BigDecimal montant) {
        Wallet wallet = getOrCreateWallet(userId);
        BigDecimal soldeAvant = wallet.getSolde();
        wallet.setSolde(soldeAvant.add(montant));
        walletRepository.save(wallet);

        enregistrerTransaction(wallet, TypeTransaction.REMBOURSEMENT, montant, soldeAvant,
                wallet.getSolde(), "Remboursement commande #" + commandeId, "CMD-" + commandeId);

        return toDTO(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionWalletDTO> getHistoriqueTransactions(Long userId, Pageable pageable) {
        Wallet wallet = getOrCreateWallet(userId);
        return transactionWalletRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable)
                .map(this::toTransactionDTO);
    }

    private Wallet getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
            Wallet newWallet = Wallet.builder().user(user).solde(BigDecimal.ZERO).build();
            return walletRepository.save(newWallet);
        });
    }

    private void enregistrerTransaction(Wallet wallet, TypeTransaction type, BigDecimal montant,
                                         BigDecimal soldeAvant, BigDecimal soldeApres,
                                         String description, String referenceExterne) {
        TransactionWallet tx = TransactionWallet.builder()
                .wallet(wallet)
                .type(type)
                .montant(montant)
                .soldeAvant(soldeAvant)
                .soldeApres(soldeApres)
                .description(description)
                .referenceExterne(referenceExterne)
                .build();
        transactionWalletRepository.save(tx);
    }

    private WalletDTO toDTO(Wallet w) {
        WalletDTO dto = new WalletDTO();
        dto.setId(w.getId());
        dto.setSolde(w.getSolde());
        dto.setCreatedAt(w.getCreatedAt());
        dto.setUpdatedAt(w.getUpdatedAt());
        if (w.getUser() != null) {
            dto.setUserId(w.getUser().getId());
            dto.setUserNom(w.getUser().getNom());
            dto.setUserPrenom(w.getUser().getPrenom());
        }
        return dto;
    }

    private TransactionWalletDTO toTransactionDTO(TransactionWallet tx) {
        TransactionWalletDTO dto = new TransactionWalletDTO();
        dto.setId(tx.getId());
        dto.setType(tx.getType());
        dto.setMontant(tx.getMontant());
        dto.setSoldeAvant(tx.getSoldeAvant());
        dto.setSoldeApres(tx.getSoldeApres());
        dto.setDescription(tx.getDescription());
        dto.setReferenceExterne(tx.getReferenceExterne());
        dto.setCreatedAt(tx.getCreatedAt());
        return dto;
    }
}
