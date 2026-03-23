package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.cart.GainsLivreurDTO;
import ma.mysuguclientapp.dtos.cart.GainsSummaryDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.GainsLivreur;
import ma.mysuguclientapp.repositories.GainsLivreurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GainsLivreurServiceImpl {

    private static final BigDecimal COMMISSION_PLATEFORME_RATE = new BigDecimal("0.15"); // 15%

    private final GainsLivreurRepository gainsLivreurRepository;

    @Transactional
    public GainsLivreur enregistrerGains(Commande commande) {
        BigDecimal fraisLivraison = commande.getFraisLivraison() != null ? commande.getFraisLivraison() : BigDecimal.ZERO;
        BigDecimal commission = fraisLivraison.multiply(COMMISSION_PLATEFORME_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montantNet = fraisLivraison.subtract(commission);

        GainsLivreur gains = GainsLivreur.builder()
                .livreur(commande.getLivreur())
                .commande(commande)
                .montant(fraisLivraison)
                .fraisLivraison(fraisLivraison)
                .commissionPlateforme(commission)
                .montantNet(montantNet)
                .estPaye(false)
                .build();
        return gainsLivreurRepository.save(gains);
    }

    @Transactional(readOnly = true)
    public Page<GainsLivreurDTO> getHistoriqueGains(Long livreurId, Pageable pageable) {
        return gainsLivreurRepository.findByLivreurIdOrderByCreatedAtDesc(livreurId, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public GainsSummaryDTO getSummary(Long livreurId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        LocalDateTime debutSemaine = now.minusDays(7);
        LocalDateTime debutMois = now.minusDays(30);

        return GainsSummaryDTO.builder()
                .totalGains(gainsLivreurRepository.sumGainsByLivreurId(livreurId))
                .gainsAujourdhui(gainsLivreurRepository.sumGainsByLivreurIdAndPeriode(livreurId, debutJour, now))
                .gainsSemaine(gainsLivreurRepository.sumGainsByLivreurIdAndPeriode(livreurId, debutSemaine, now))
                .gainsMois(gainsLivreurRepository.sumGainsByLivreurIdAndPeriode(livreurId, debutMois, now))
                .nombreLivraisons(gainsLivreurRepository.countByLivreurId(livreurId))
                .build();
    }

    private GainsLivreurDTO toDTO(GainsLivreur g) {
        GainsLivreurDTO dto = new GainsLivreurDTO();
        dto.setId(g.getId());
        dto.setMontant(g.getMontant());
        dto.setFraisLivraison(g.getFraisLivraison());
        dto.setCommissionPlateforme(g.getCommissionPlateforme());
        dto.setMontantNet(g.getMontantNet());
        dto.setEstPaye(g.getEstPaye());
        dto.setPayeAt(g.getPayeAt());
        dto.setCreatedAt(g.getCreatedAt());
        if (g.getCommande() != null) {
            dto.setCommandeId(g.getCommande().getId());
            dto.setNumeroCommande(g.getCommande().getNumeroCommande());
        }
        return dto;
    }
}
