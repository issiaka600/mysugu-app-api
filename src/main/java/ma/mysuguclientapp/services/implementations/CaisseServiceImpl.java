package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.caisse.*;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.*;
import ma.mysuguclientapp.repositories.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaisseServiceImpl {

    private final CaisseLivreurRepository caisseLivreurRepository;
    private final TransactionCaisseRepository transactionCaisseRepository;
    private final GainsLivreurRepository gainsLivreurRepository;
    private final ParametresCaisseRepository parametresCaisseRepository;
    private final ParametresPaiementRestaurantRepository parametresPaiementRestaurantRepository;
    private final DetteRestaurantRepository detteRestaurantRepository;
    private final PaiementRestaurantRepository paiementRestaurantRepository;
    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final NotificationServiceImpl notificationService;

    // =====================================================================
    // LECTURE
    // =====================================================================

    @Transactional
    public CaisseLivreurDTO getPositionLivreur(Long livreurId) {
        CaisseLivreur caisse = getOrCreateCaisse(livreurId);
        return toDTO(caisse);
    }

    @Transactional
    public Page<TransactionCaisseDTO> getHistoriqueCaisse(Long livreurId, Pageable pageable) {
        CaisseLivreur caisse = getOrCreateCaisse(livreurId);
        return transactionCaisseRepository
                .findByCaisseLivreurIdOrderByCreatedAtDesc(caisse.getId(), pageable)
                .map(this::toTransactionDTO);
    }

    @Transactional(readOnly = true)
    public BordCaisseAdminDTO getBordAdmin() {
        ParametresCaisse params = getParams();
        List<CaisseLivreur> caisses = caisseLivreurRepository.findAvecSoldePositif();

        BigDecimal totalEspeces = caisses.stream()
                .map(CaisseLivreur::getSoldeCourant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalGains = caisses.stream()
                .map(c -> calculerGainsDus(c.getLivreur().getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDettes = detteRestaurantRepository.sumTotalDetteEnAttente();

        List<CaisseLivreurDTO> details = caisses.stream().map(this::toDTO).collect(Collectors.toList());
        long enAlerte = details.stream().filter(d -> d.isAlertePlafond() || d.isAlerteIntervalle()).count();

        return BordCaisseAdminDTO.builder()
                .totalEspecesLivreurs(totalEspeces)
                .totalGainsDusLivreurs(totalGains)
                .netDuParLivreurs(totalEspeces.subtract(totalGains))
                .totalDetteRestaurants(totalDettes)
                .nombreLivreursEnAlerte((int) enAlerte)
                .detailLivreurs(details)
                .build();
    }

    // =====================================================================
    // INFO LIVREUR AVANT COLLECTE
    // =====================================================================

    @Transactional
    public InfoPaiementCommandeDTO getInfoPaiementCommande(Long commandeId, Long livreurId) {
        Commande commande = getCommande(commandeId);
        CaisseLivreur caisse = getOrCreateCaisse(livreurId);
        ParametresCaisse params = getParams();

        BigDecimal montantCommande = commande.getMontantTotal();
        BigDecimal fraisLivraison = commande.getFraisLivraison() != null ? commande.getFraisLivraison() : BigDecimal.ZERO;
        BigDecimal totalClient = montantCommande.add(fraisLivraison);
        BigDecimal gainNet = fraisLivraison.multiply(BigDecimal.ONE.subtract(params.getTauxCommissionPlateforme()))
                .setScale(2, RoundingMode.HALF_UP);

        ParametresPaiementRestaurant paramResto = parametresPaiementRestaurantRepository
                .findByRestaurantId(commande.getRestaurant().getId()).orElse(null);

        boolean doitPayer = paramResto != null &&
                paramResto.getModePaiement() == ModePaiementRestaurant.PAR_COMMANDE &&
                commande.getMethodePaiement() == MethodePaiement.ESPECES;

        boolean suffisant = !doitPayer || caisse.getSoldeCourant().compareTo(montantCommande) >= 0;

        return InfoPaiementCommandeDTO.builder()
                .commandeId(commande.getId())
                .numeroCommande(commande.getNumeroCommande())
                .restaurantNom(commande.getRestaurant().getNom())
                .clientNom(commande.getClient().getNom() + " " + commande.getClient().getPrenom())
                .montantCommande(montantCommande)
                .fraisLivraison(fraisLivraison)
                .totalAEncaisserClient(totalClient)
                .modePaiementRestaurant(paramResto != null ? paramResto.getModePaiement().name() : "PERIODIQUE")
                .doitPayerRestaurant(doitPayer)
                .montantAPayerRestaurant(doitPayer ? montantCommande : BigDecimal.ZERO)
                .soldeCaisseLivreur(caisse.getSoldeCourant())
                .liquiditesSuffisantes(suffisant)
                .gainNetLivreur(gainNet)
                .build();
    }

    // =====================================================================
    // MOUVEMENTS DE CAISSE
    // =====================================================================

    /**
     * Appelé quand le livreur paie le restaurant à la récupération du plat (mode PAR_COMMANDE).
     */
    @Transactional
    public CaisseLivreurDTO confirmerPaiementRestaurant(Long commandeId, Long livreurId) {
        Commande commande = getCommande(commandeId);

        // Vérifier mode restaurant
        ParametresPaiementRestaurant paramResto = parametresPaiementRestaurantRepository
                .findByRestaurantId(commande.getRestaurant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ce restaurant n'est pas configuré en mode PAR_COMMANDE"));

        if (paramResto.getModePaiement() != ModePaiementRestaurant.PAR_COMMANDE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ce restaurant est en mode PERIODIQUE, le livreur ne paie pas à la récupération");
        }

        CaisseLivreur caisse = getOrCreateCaisse(livreurId);
        BigDecimal montantResto = commande.getMontantTotal();

        if (caisse.getSoldeCourant().compareTo(montantResto) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Liquidités insuffisantes dans la caisse du livreur (" +
                            caisse.getSoldeCourant() + " MAD disponible, " + montantResto + " MAD requis)");
        }

        BigDecimal soldeAvant = caisse.getSoldeCourant();
        caisse.setSoldeCourant(soldeAvant.subtract(montantResto));
        resetAlertes(caisse);
        caisseLivreurRepository.save(caisse);

        enregistrerTransaction(caisse, TypeTransactionCaisse.PAIEMENT_RESTAURANT,
                montantResto, soldeAvant,
                "Paiement restaurant " + commande.getRestaurant().getNom() +
                        " pour commande #" + commande.getNumeroCommande(), commande, null);

        // Mettre à jour la dette restaurant
        detteRestaurantRepository.findByCommandeId(commandeId).ifPresent(dette -> {
            dette.setStatut(StatutDetteRestaurant.PAYE_PAR_LIVREUR);
            dette.setPayeParLivreur(caisse.getLivreur());
            dette.setDatePaiement(LocalDateTime.now());
            detteRestaurantRepository.save(dette);
        });

        return toDTO(caisse);
    }

    /**
     * Appelé automatiquement quand une commande espèces est marquée LIVREE.
     */
    @Transactional
    public CaisseLivreur enregistrerCollecteClient(Commande commande) {
        if (commande.getMethodePaiement() != MethodePaiement.ESPECES) return null;
        if (commande.getLivreur() == null) return null;

        CaisseLivreur caisse = getOrCreateCaisse(commande.getLivreur().getId());
        BigDecimal totalClient = commande.getMontantTotal()
                .add(commande.getFraisLivraison() != null ? commande.getFraisLivraison() : BigDecimal.ZERO);

        BigDecimal soldeAvant = caisse.getSoldeCourant();
        caisse.setSoldeCourant(soldeAvant.add(totalClient));
        caisse.setTotalCollecteSession(caisse.getTotalCollecteSession().add(totalClient));
        caisseLivreurRepository.save(caisse);

        enregistrerTransaction(caisse, TypeTransactionCaisse.COLLECTE_CLIENT,
                totalClient, soldeAvant,
                "Encaissement client commande #" + commande.getNumeroCommande(), commande, null);

        // Créer la dette restaurant (mode PERIODIQUE ou PAR_COMMANDE si pas encore payée)
        creerDetteRestaurantSiNecessaire(commande);

        // Vérifier seuil et alerter si nécessaire
        verifierEtAlerter(caisse);

        return caisse;
    }

    /**
     * Avance de liquidités accordée par la plateforme au livreur.
     */
    @Transactional
    public CaisseLivreurDTO accordAvanceLivreur(Long livreurId, BigDecimal montant, Long adminId, String note) {
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le montant doit être positif");
        }
        CaisseLivreur caisse = getOrCreateCaisse(livreurId);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin introuvable"));

        BigDecimal soldeAvant = caisse.getSoldeCourant();
        caisse.setSoldeCourant(soldeAvant.add(montant));
        caisseLivreurRepository.save(caisse);

        enregistrerTransaction(caisse, TypeTransactionCaisse.AVANCE_LIVREUR,
                montant, soldeAvant,
                note != null ? note : "Avance fond de caisse", null, admin);

        return toDTO(caisse);
    }

    /**
     * Réconciliation complète : le livreur se présente à l'admin, remet les espèces dues.
     */
    @Transactional
    public ReconciliationResultDTO reconcilier(ReconciliationDTO dto, Long adminId) {
        CaisseLivreur caisse = caisseLivreurRepository.findByLivreurId(dto.getLivreurId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune caisse pour ce livreur"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin introuvable"));

        BigDecimal soldeCourant = caisse.getSoldeCourant();
        BigDecimal gainsDus = calculerGainsDus(dto.getLivreurId());
        BigDecimal montantDuCalcule = soldeCourant.subtract(gainsDus).max(BigDecimal.ZERO);
        BigDecimal montantRemis = dto.getMontantRemis() != null ? dto.getMontantRemis() : montantDuCalcule;
        BigDecimal ecart = montantDuCalcule.subtract(montantRemis);

        // Enregistrer la remise
        BigDecimal soldeAvant = caisse.getSoldeCourant();
        caisse.setSoldeCourant(soldeAvant.subtract(montantRemis).subtract(gainsDus.min(soldeCourant.subtract(montantRemis).max(BigDecimal.ZERO))));
        // Plus simplement : après réconciliation, solde = 0 si tout est réglé
        caisse.setSoldeCourant(BigDecimal.ZERO);
        caisse.setTotalCollecteSession(BigDecimal.ZERO);
        caisse.setDerniereReconciliation(LocalDateTime.now());
        caisse.setAlertePlafondEnvoyee(false);
        caisse.setAlerteIntervalleEnvoyee(false);
        caisseLivreurRepository.save(caisse);

        // Transaction remise
        enregistrerTransaction(caisse, TypeTransactionCaisse.REMISE_PLATEFORME,
                montantRemis, soldeAvant,
                "Réconciliation caisse" + (dto.getNote() != null ? " - " + dto.getNote() : ""),
                null, admin);

        // Transaction versement gains
        if (gainsDus.compareTo(BigDecimal.ZERO) > 0) {
            enregistrerTransaction(caisse, TypeTransactionCaisse.VERSEMENT_GAINS,
                    gainsDus, BigDecimal.ZERO,
                    "Versement gains au livreur lors réconciliation", null, admin);

            // Marquer les gains comme payés
            marquerGainsPaies(dto.getLivreurId());
        }

        String message = ecart.compareTo(BigDecimal.ZERO) == 0
                ? "Réconciliation parfaite."
                : ecart.compareTo(BigDecimal.ZERO) > 0
                ? "Écart de " + ecart + " MAD — livreur doit encore."
                : "Livreur a versé " + ecart.abs() + " MAD de trop — remboursement à prévoir.";

        return ReconciliationResultDTO.builder()
                .livreurId(dto.getLivreurId())
                .livreurNom(caisse.getLivreur().getNom() + " " + caisse.getLivreur().getPrenom())
                .soldeCourantAvant(soldeAvant)
                .gainsDus(gainsDus)
                .montantDuCalcule(montantDuCalcule)
                .montantRemis(montantRemis)
                .ecart(ecart)
                .dateReconciliation(LocalDateTime.now())
                .message(message)
                .build();
    }

    // =====================================================================
    // PARAMÈTRES
    // =====================================================================

    @Transactional(readOnly = true)
    public ParametresCaisseDTO getParametres() {
        return toParamsDTO(getParams());
    }

    @Transactional
    public ParametresCaisseDTO updateParametres(ParametresCaisseDTO dto) {
        ParametresCaisse params = getParams();
        if (dto.getPlafondCaisseLivreur() != null) params.setPlafondCaisseLivreur(dto.getPlafondCaisseLivreur());
        if (dto.getSeuilAlertePourcentage() != null) params.setSeuilAlertePourcentage(dto.getSeuilAlertePourcentage());
        if (dto.getIntervalleReconciliationHeures() != null) params.setIntervalleReconciliationHeures(dto.getIntervalleReconciliationHeures());
        if (dto.getTauxCommissionPlateforme() != null) params.setTauxCommissionPlateforme(dto.getTauxCommissionPlateforme());
        if (dto.getPeriodicitePaiementRestaurantJours() != null) params.setPeriodicitePaiementRestaurantJours(dto.getPeriodicitePaiementRestaurantJours());
        return toParamsDTO(parametresCaisseRepository.save(params));
    }

    @Transactional
    public CaisseLivreurDTO setPlafondPersonnaliseLivreur(Long livreurId, BigDecimal plafond) {
        CaisseLivreur caisse = getOrCreateCaisse(livreurId);
        caisse.setPlafondPersonnalise(plafond);
        return toDTO(caisseLivreurRepository.save(caisse));
    }

    // =====================================================================
    // HELPERS PRIVÉS
    // =====================================================================

    public CaisseLivreur getOrCreateCaisse(Long livreurId) {
        return caisseLivreurRepository.findByLivreurId(livreurId).orElseGet(() -> {
            User livreur = userRepository.findById(livreurId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livreur introuvable"));
            CaisseLivreur caisse = CaisseLivreur.builder()
                    .livreur(livreur)
                    .soldeCourant(BigDecimal.ZERO)
                    .totalCollecteSession(BigDecimal.ZERO)
                    .alertePlafondEnvoyee(false)
                    .alerteIntervalleEnvoyee(false)
                    .build();
            return caisseLivreurRepository.save(caisse);
        });
    }

    private void creerDetteRestaurantSiNecessaire(Commande commande) {
        // Éviter les doublons
        if (detteRestaurantRepository.findByCommandeId(commande.getId()).isPresent()) return;

        ParametresPaiementRestaurant paramResto = parametresPaiementRestaurantRepository
                .findByRestaurantId(commande.getRestaurant().getId()).orElse(null);

        // Pour mode PAR_COMMANDE la dette est créée mais immédiatement marquée EN_ATTENTE
        // (sera mise à jour quand livreur confirme paiement)
        // Pour PERIODIQUE on la crée aussi
        DetteRestaurant dette = DetteRestaurant.builder()
                .restaurant(commande.getRestaurant())
                .commande(commande)
                .montantDu(commande.getMontantTotal())
                .statut(StatutDetteRestaurant.EN_ATTENTE)
                .build();
        detteRestaurantRepository.save(dette);
    }

    private BigDecimal calculerGainsDus(Long livreurId) {
        // Somme des gains non encore payés
        return gainsLivreurRepository.findByLivreurIdOrderByCreatedAtDesc(livreurId,
                        org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(g -> !Boolean.TRUE.equals(g.getEstPaye()))
                .map(GainsLivreur::getMontantNet)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void marquerGainsPaies(Long livreurId) {
        gainsLivreurRepository.findByLivreurIdOrderByCreatedAtDesc(livreurId,
                        org.springframework.data.domain.Pageable.unpaged())
                .forEach(g -> {
                    if (!Boolean.TRUE.equals(g.getEstPaye())) {
                        g.setEstPaye(true);
                        g.setPayeAt(LocalDateTime.now());
                        gainsLivreurRepository.save(g);
                    }
                });
    }

    private void verifierEtAlerter(CaisseLivreur caisse) {
        ParametresCaisse params = getParams();
        BigDecimal plafond = caisse.getPlafondPersonnalise() != null ?
                caisse.getPlafondPersonnalise() : params.getPlafondCaisseLivreur();
        BigDecimal seuil = plafond.multiply(BigDecimal.valueOf(params.getSeuilAlertePourcentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (caisse.getSoldeCourant().compareTo(seuil) >= 0 && !Boolean.TRUE.equals(caisse.getAlertePlafondEnvoyee())) {
            caisse.setAlertePlafondEnvoyee(true);
            caisseLivreurRepository.save(caisse);
            // Notifier livreur
            notificationService.envoyerNotificationSysteme(
                    caisse.getLivreur().getId(),
                    "Caisse à remettre",
                    "Votre caisse atteint " + caisse.getSoldeCourant() + " MAD. Merci de venir régulariser votre situation."
            );
            log.info("Alerte plafond caisse envoyée au livreur {}", caisse.getLivreur().getId());
        }
    }

    private void resetAlertes(CaisseLivreur caisse) {
        ParametresCaisse params = getParams();
        BigDecimal plafond = caisse.getPlafondPersonnalise() != null ?
                caisse.getPlafondPersonnalise() : params.getPlafondCaisseLivreur();
        BigDecimal seuil = plafond.multiply(BigDecimal.valueOf(params.getSeuilAlertePourcentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (caisse.getSoldeCourant().compareTo(seuil) < 0) {
            caisse.setAlertePlafondEnvoyee(false);
        }
    }

    private void enregistrerTransaction(CaisseLivreur caisse, TypeTransactionCaisse type,
                                         BigDecimal montant, BigDecimal soldeAvant,
                                         String description, Commande commande, User confirmedBy) {
        TransactionCaisse tx = TransactionCaisse.builder()
                .caisseLivreur(caisse)
                .type(type)
                .montant(montant)
                .soldeAvant(soldeAvant)
                .soldeApres(caisse.getSoldeCourant())
                .description(description)
                .commande(commande)
                .confirmepar(confirmedBy)
                .build();
        transactionCaisseRepository.save(tx);
    }

    private ParametresCaisse getParams() {
        return parametresCaisseRepository.findById(1L).orElseGet(() -> {
            ParametresCaisse defaults = ParametresCaisse.builder()
                    .id(1L)
                    .plafondCaisseLivreur(new BigDecimal("500.00"))
                    .seuilAlertePourcentage(80)
                    .intervalleReconciliationHeures(48)
                    .tauxCommissionPlateforme(new BigDecimal("0.1500"))
                    .periodicitePaiementRestaurantJours(7)
                    .build();
            return parametresCaisseRepository.save(defaults);
        });
    }

    private Commande getCommande(Long commandeId) {
        return commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable: " + commandeId));
    }

    // =====================================================================
    // MAPPERS
    // =====================================================================

    public CaisseLivreurDTO toDTO(CaisseLivreur caisse) {
        ParametresCaisse params = getParams();
        BigDecimal plafond = caisse.getPlafondPersonnalise() != null ?
                caisse.getPlafondPersonnalise() : params.getPlafondCaisseLivreur();
        BigDecimal seuil = plafond.multiply(BigDecimal.valueOf(params.getSeuilAlertePourcentage()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal gainsDus = calculerGainsDus(caisse.getLivreur().getId());
        BigDecimal montantDu = caisse.getSoldeCourant().subtract(gainsDus);
        boolean alerteIntervalle = caisse.getDerniereReconciliation() == null ||
                caisse.getDerniereReconciliation().plusHours(params.getIntervalleReconciliationHeures())
                        .isBefore(LocalDateTime.now());

        return CaisseLivreurDTO.builder()
                .id(caisse.getId())
                .livreurId(caisse.getLivreur().getId())
                .livreurNom(caisse.getLivreur().getNom())
                .livreurPrenom(caisse.getLivreur().getPrenom())
                .livreurTelephone(caisse.getLivreur().getTelephone())
                .soldeCourant(caisse.getSoldeCourant())
                .gainsDusNonVerses(gainsDus)
                .montantDuPlateforme(montantDu)
                .plafondEffectif(plafond)
                .seuilAlerte(seuil)
                .alertePlafond(caisse.getSoldeCourant().compareTo(seuil) >= 0)
                .alerteIntervalle(alerteIntervalle && caisse.getSoldeCourant().compareTo(BigDecimal.ZERO) > 0)
                .derniereReconciliation(caisse.getDerniereReconciliation())
                .updatedAt(caisse.getUpdatedAt())
                .build();
    }

    private TransactionCaisseDTO toTransactionDTO(TransactionCaisse tx) {
        TransactionCaisseDTO dto = new TransactionCaisseDTO();
        dto.setId(tx.getId());
        dto.setType(tx.getType());
        dto.setMontant(tx.getMontant());
        dto.setSoldeAvant(tx.getSoldeAvant());
        dto.setSoldeApres(tx.getSoldeApres());
        dto.setDescription(tx.getDescription());
        dto.setCreatedAt(tx.getCreatedAt());
        if (tx.getCommande() != null) {
            dto.setCommandeId(tx.getCommande().getId());
            dto.setNumeroCommande(tx.getCommande().getNumeroCommande());
        }
        if (tx.getConfirmepar() != null) {
            dto.setConfirmeParNom(tx.getConfirmepar().getNom() + " " + tx.getConfirmepar().getPrenom());
        }
        return dto;
    }

    private ParametresCaisseDTO toParamsDTO(ParametresCaisse p) {
        ParametresCaisseDTO dto = new ParametresCaisseDTO();
        dto.setPlafondCaisseLivreur(p.getPlafondCaisseLivreur());
        dto.setSeuilAlertePourcentage(p.getSeuilAlertePourcentage());
        dto.setIntervalleReconciliationHeures(p.getIntervalleReconciliationHeures());
        dto.setTauxCommissionPlateforme(p.getTauxCommissionPlateforme());
        dto.setPeriodicitePaiementRestaurantJours(p.getPeriodicitePaiementRestaurantJours());
        return dto;
    }
}
