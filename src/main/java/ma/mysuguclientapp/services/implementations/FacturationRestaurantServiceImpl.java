package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.caisse.DetteRestaurantDTO;
import ma.mysuguclientapp.dtos.caisse.PaiementRestaurantDTO;
import ma.mysuguclientapp.dtos.caisse.ParametresPaiementRestaurantDTO;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.ModePaiementRestaurant;
import ma.mysuguclientapp.enumerations.ModeVersementRestaurant;
import ma.mysuguclientapp.enumerations.StatutDetteRestaurant;
import ma.mysuguclientapp.enumerations.StatutPaiementRestaurant;
import ma.mysuguclientapp.repositories.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacturationRestaurantServiceImpl {

    private final DetteRestaurantRepository detteRestaurantRepository;
    private final PaiementRestaurantRepository paiementRestaurantRepository;
    private final ParametresPaiementRestaurantRepository parametresPaiementRestaurantRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    // =====================================================================
    // PARAMÈTRES PAIEMENT RESTAURANT
    // =====================================================================

    @Transactional(readOnly = true)
    public ParametresPaiementRestaurantDTO getParametres(Long restaurantId) {
        ParametresPaiementRestaurant params = parametresPaiementRestaurantRepository
                .findByRestaurantId(restaurantId)
                .orElseGet(() -> creerParametresDefaut(restaurantId));
        return toParamsDTO(params);
    }

    @Transactional
    public ParametresPaiementRestaurantDTO updateParametres(Long restaurantId, ParametresPaiementRestaurantDTO dto) {
        ParametresPaiementRestaurant params = parametresPaiementRestaurantRepository
                .findByRestaurantId(restaurantId)
                .orElseGet(() -> creerParametresDefaut(restaurantId));

        if (dto.getModePaiement() != null) params.setModePaiement(dto.getModePaiement());
        if (dto.getPeriodicitéJours() != null) params.setPeriodicitéJours(dto.getPeriodicitéJours());
        if (dto.getModeVersement() != null) params.setModeVersement(dto.getModeVersement());
        if (dto.getRib() != null) params.setRib(dto.getRib());
        if (dto.getNomBeneficiaire() != null) params.setNomBeneficiaire(dto.getNomBeneficiaire());

        return toParamsDTO(parametresPaiementRestaurantRepository.save(params));
    }

    // =====================================================================
    // DETTES RESTAURANT
    // =====================================================================

    @Transactional(readOnly = true)
    public List<DetteRestaurantDTO> getDettesEnAttente(Long restaurantId) {
        return detteRestaurantRepository
                .findByRestaurantIdAndStatut(restaurantId, StatutDetteRestaurant.EN_ATTENTE)
                .stream().map(this::toDettesDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalDetteEnAttente(Long restaurantId) {
        BigDecimal total = detteRestaurantRepository.sumDetteEnAttenteByRestaurant(restaurantId);
        return total != null ? total : BigDecimal.ZERO;
    }

    // =====================================================================
    // PAIEMENTS PÉRIODIQUES
    // =====================================================================

    /**
     * Crée un paiement groupé couvrant toutes les dettes EN_ATTENTE du restaurant.
     */
    @Transactional
    public PaiementRestaurantDTO creerPaiementPeriodique(Long restaurantId, Long adminId,
                                                          LocalDateTime periodeDebut,
                                                          LocalDateTime periodeFin,
                                                          String note) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin introuvable"));

        List<DetteRestaurant> dettes = detteRestaurantRepository
                .findByRestaurantIdAndStatut(restaurantId, StatutDetteRestaurant.EN_ATTENTE);

        if (dettes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune dette en attente pour ce restaurant");
        }

        ParametresPaiementRestaurant params = parametresPaiementRestaurantRepository
                .findByRestaurantId(restaurantId).orElse(null);
        ModeVersementRestaurant modeVersement = params != null ? params.getModeVersement() : ModeVersementRestaurant.VIREMENT_BANCAIRE;

        BigDecimal montantTotal = dettes.stream()
                .map(DetteRestaurant::getMontantDu)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PaiementRestaurant paiement = PaiementRestaurant.builder()
                .restaurant(restaurant)
                .montantTotal(montantTotal)
                .periodeDebut(periodeDebut)
                .periodeFin(periodeFin)
                .modeVersement(modeVersement)
                .reference("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .statut(StatutPaiementRestaurant.EFFECTUE)
                .effectuePar(admin)
                .note(note)
                .datePaiement(LocalDateTime.now())
                .dettes(dettes)
                .build();

        PaiementRestaurant savedPaiement = paiementRestaurantRepository.save(paiement);

        // Marquer toutes les dettes comme incluses dans le virement
        dettes.forEach(dette -> {
            dette.setStatut(StatutDetteRestaurant.INCLUS_DANS_VIREMENT);
            dette.setPaiementRestaurant(savedPaiement);
            dette.setDatePaiement(LocalDateTime.now());
        });
        detteRestaurantRepository.saveAll(dettes);

        log.info("Paiement restaurant {} créé: {} MAD pour {} dettes (réf. {})",
                restaurant.getNom(), montantTotal, dettes.size(), savedPaiement.getReference());

        return toPaiementDTO(savedPaiement);
    }

    @Transactional(readOnly = true)
    public Page<PaiementRestaurantDTO> getHistoriquePaiements(Long restaurantId, Pageable pageable) {
        return paiementRestaurantRepository
                .findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable)
                .map(this::toPaiementDTO);
    }

    @Transactional(readOnly = true)
    public List<DetteRestaurantDTO> getDettesParPaiement(Long paiementId) {
        return detteRestaurantRepository.findByPaiementRestaurantId(paiementId)
                .stream().map(this::toDettesDTO).collect(Collectors.toList());
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private ParametresPaiementRestaurant creerParametresDefaut(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
        ParametresPaiementRestaurant params = ParametresPaiementRestaurant.builder()
                .restaurant(restaurant)
                .modePaiement(ModePaiementRestaurant.PERIODIQUE)
                .periodicitéJours(7)
                .modeVersement(ModeVersementRestaurant.VIREMENT_BANCAIRE)
                .build();
        return parametresPaiementRestaurantRepository.save(params);
    }

    private ParametresPaiementRestaurantDTO toParamsDTO(ParametresPaiementRestaurant p) {
        ParametresPaiementRestaurantDTO dto = new ParametresPaiementRestaurantDTO();
        dto.setRestaurantId(p.getRestaurant().getId());
        dto.setRestaurantNom(p.getRestaurant().getNom());
        dto.setModePaiement(p.getModePaiement());
        dto.setPeriodicitéJours(p.getPeriodicitéJours());
        dto.setModeVersement(p.getModeVersement());
        dto.setRib(p.getRib());
        dto.setNomBeneficiaire(p.getNomBeneficiaire());
        return dto;
    }

    private DetteRestaurantDTO toDettesDTO(DetteRestaurant d) {
        DetteRestaurantDTO dto = new DetteRestaurantDTO();
        dto.setId(d.getId());
        dto.setRestaurantId(d.getRestaurant().getId());
        dto.setRestaurantNom(d.getRestaurant().getNom());
        dto.setCommandeId(d.getCommande().getId());
        dto.setNumeroCommande(d.getCommande().getNumeroCommande());
        dto.setMontantDu(d.getMontantDu());
        dto.setStatut(d.getStatut());
        dto.setDatePaiement(d.getDatePaiement());
        if (d.getPayeParLivreur() != null) {
            dto.setPayeParLivreurNom(d.getPayeParLivreur().getNom() + " " + d.getPayeParLivreur().getPrenom());
        }
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }

    private PaiementRestaurantDTO toPaiementDTO(PaiementRestaurant p) {
        PaiementRestaurantDTO dto = new PaiementRestaurantDTO();
        dto.setId(p.getId());
        dto.setRestaurantId(p.getRestaurant().getId());
        dto.setRestaurantNom(p.getRestaurant().getNom());
        dto.setMontantTotal(p.getMontantTotal());
        dto.setPeriodeDebut(p.getPeriodeDebut());
        dto.setPeriodeFin(p.getPeriodeFin());
        dto.setModeVersement(p.getModeVersement());
        dto.setReference(p.getReference());
        dto.setStatut(p.getStatut());
        dto.setNote(p.getNote());
        dto.setDatePaiement(p.getDatePaiement());
        if (p.getEffectuePar() != null) {
            dto.setEffectueParNom(p.getEffectuePar().getNom() + " " + p.getEffectuePar().getPrenom());
        }
        dto.setNombreCommandes(p.getDettes() != null ? p.getDettes().size() : 0);
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}
