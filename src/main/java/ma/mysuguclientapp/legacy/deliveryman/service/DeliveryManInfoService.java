package ma.mysuguclientapp.legacy.deliveryman.service;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.CaisseLivreur;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.TypeTransactionCaisse;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.implementations.GainsLivreurServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agrégation de l'objet /info du livreur (contrat 6valley §2.1) à partir des entités natives
 * MySugu. Implémente le modèle argent résolu (techspec §7) :
 *   current_balance  = Σ gains nets non payés
 *   cash_in_hand     = CaisseLivreur.soldeCourant
 *   withdrawable     = max(0, current_balance − cash_in_hand − pending_withdraw)
 *   total_earn       = current_balance + total_withdraw
 *   total_deposit    = Σ transactions REMISE_PLATEFORME
 *
 * Clés fragiles garanties présentes : is_online (0/1), identity_image (chaîne JSON "[]").
 */
@Service
@RequiredArgsConstructor
public class DeliveryManInfoService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GainsLivreurServiceImpl gainsLivreurService;
    private final CaisseLivreurRepository caisseLivreurRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final TransactionCaisseRepository transactionCaisseRepository;
    private final CommandeRepository commandeRepository;
    private final AvisRepository avisRepository;

    /** current_balance = total gains nets − total retraits approuvés (gère les retraits partiels). */
    private BigDecimal currentBalance(Long livreurId) {
        BigDecimal totalGains = nz(gainsLivreurService.getTotalGainsNet(livreurId));
        BigDecimal totalWithdraw = nz(demandeRetraitRepository.sumMontantByLivreurAndStatut(
                livreurId, ma.mysuguclientapp.enumerations.StatutRetrait.APPROUVE));
        return totalGains.subtract(totalWithdraw);
    }

    /** Solde retirable = max(0, current_balance − cash_in_hand − pending_withdraw). Techspec §7. */
    @Transactional(readOnly = true)
    public BigDecimal withdrawable(Long livreurId) {
        BigDecimal cashInHand = caisseLivreurRepository.findByLivreurId(livreurId)
                .map(c -> nz(c.getSoldeCourant())).orElse(BigDecimal.ZERO);
        BigDecimal pending = nz(demandeRetraitRepository.sumMontantByLivreurAndStatut(
                livreurId, ma.mysuguclientapp.enumerations.StatutRetrait.EN_ATTENTE));
        return currentBalance(livreurId).subtract(cashInHand).subtract(pending).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildInfo(User livreur) {
        Long id = livreur.getId();

        BigDecimal currentBalance = currentBalance(id);
        CaisseLivreur caisse = caisseLivreurRepository.findByLivreurId(id).orElse(null);
        BigDecimal cashInHand = caisse != null ? nz(caisse.getSoldeCourant()) : BigDecimal.ZERO;
        BigDecimal pendingWithdraw = nz(demandeRetraitRepository.sumMontantByLivreurAndStatut(
                id, ma.mysuguclientapp.enumerations.StatutRetrait.EN_ATTENTE));
        BigDecimal totalWithdraw = nz(demandeRetraitRepository.sumMontantByLivreurAndStatut(
                id, ma.mysuguclientapp.enumerations.StatutRetrait.APPROUVE));
        BigDecimal totalDeposit = caisse != null
                ? nz(transactionCaisseRepository.sumMontantByCaisseAndType(caisse.getId(), TypeTransactionCaisse.REMISE_PLATEFORME))
                : BigDecimal.ZERO;
        BigDecimal withdrawable = currentBalance.subtract(cashInHand).subtract(pendingWithdraw).max(BigDecimal.ZERO);
        BigDecimal totalEarn = currentBalance.add(totalWithdraw);

        long completed = commandeRepository.countByLivreurIdAndStatut(id, StatutCommande.LIVREE);
        long total = commandeRepository.countByLivreurId(id);
        long pause = commandeRepository.countByLivreurIdAndEnPauseTrue(id);
        long pending = Math.max(0, total - completed);
        Double avg = avisRepository.getAverageNoteLivreur(id);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("f_name", nvl(livreur.getPrenom()));
        m.put("l_name", nvl(livreur.getNom()));
        m.put("phone", nvl(livreur.getTelephone()));
        m.put("email", nvl(livreur.getEmail()));
        m.put("image", nvl(livreur.getAvatar()));
        // Clés fragiles :
        m.put("is_online", Boolean.TRUE.equals(livreur.getLivreurDisponible()) ? 1 : 0);
        m.put("identity_number", "");
        m.put("identity_type", "");
        m.put("identity_image", "[]"); // chaîne JSON (jsonDecode côté app)
        m.put("created_at", livreur.getCreatedAt() != null ? livreur.getCreatedAt().format(TS) : null);
        m.put("updated_at", livreur.getUpdatedAt() != null ? livreur.getUpdatedAt().format(TS) : null);
        m.put("country_code", nvl(livreur.getCountryCode()));
        m.put("address", livreur.getLocalisation() != null ? nvl(livreur.getLocalisation().getAdresse()) : "");
        // Argent :
        m.put("withdrawable_balance", withdrawable);
        m.put("current_balance", currentBalance);
        m.put("cash_in_hand", cashInHand);
        m.put("pending_withdraw", pendingWithdraw);
        m.put("total_withdraw", totalWithdraw);
        m.put("total_deposit", totalDeposit);
        m.put("total_earn", totalEarn);
        // Compteurs :
        m.put("completed_delivery", completed);
        m.put("total_delivery", total);
        m.put("pause_delivery", pause);
        m.put("pending_delivery", pending);
        // Banque :
        m.put("bank_name", nvl(livreur.getBankName()));
        m.put("branch", nvl(livreur.getBranch()));
        m.put("account_no", nvl(livreur.getAccountNo()));
        m.put("holder_name", nvl(livreur.getHolderName()));
        // Note moyenne (ignoré par l'app mais renvoyé par 6valley) :
        m.put("average_rating", avg != null ? avg : 0);
        return m;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
