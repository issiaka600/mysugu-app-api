package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.CategorieRestaurantDTO;
import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.CommandeUpdateStatusDTO;
import ma.mysuguclientapp.dtos.AssignThirdPartyDeliveryDTO;
import ma.mysuguclientapp.dtos.UpdatePaymentStatusDTO;
import ma.mysuguclientapp.dtos.DeliveryChargeDateUpdateDTO;
import ma.mysuguclientapp.dtos.OrderWiseProductUploadDTO;
import ma.mysuguclientapp.dtos.LigneCommandeCreateDTO;
import ma.mysuguclientapp.dtos.LigneCommandeDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.ModeDisponibilitePlat;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.enumerations.MethodePaiement;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.TypeNotification;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.enumerations.TypeReduction;
import ma.mysuguclientapp.repositories.AvisRepository;
import ma.mysuguclientapp.repositories.CodePromoRepository;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.LigneCommandeRepository;
import ma.mysuguclientapp.repositories.ParametresCaisseRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import ma.mysuguclientapp.services.interfaces.NotificationService;
import ma.mysuguclientapp.services.interfaces.StripeService;
import ma.mysuguclientapp.util.CommandeNumberGenerator;
import ma.mysuguclientapp.util.Constants;
import ma.mysuguclientapp.services.integrations.TikTakOrderIntegrationService;
import ma.mysuguclientapp.services.tracking.TrackingLocationStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CommandeServiceImpl implements CommandeService {
    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PlatRepository platRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final ParametresCaisseRepository parametresCaisseRepository;
    private final CaisseServiceImpl caisseService;
    private final GainsLivreurServiceImpl gainsLivreurService;

    /**
     * Auto-dispatch (trouverMeilleurLivreur) activé ? Défaut false = modèle FCFS
     * de l'app Tiktak : la commande est diffusée aux livreurs dispo qui la revendiquent
     * via /accept (techspec §8). Mettre à true pour réactiver l'auto-assignation native.
     */
    @org.springframework.beans.factory.annotation.Value("${dispatch.auto.enabled:false}")
    private boolean autoDispatchEnabled;
    private final NotificationService notificationService;
    private final AvisRepository avisRepository;
    private final CodePromoRepository codePromoRepository;
    private final StripeService stripeService;
    private final TikTakOrderIntegrationService tikTakOrderIntegrationService;
    private final TrackingLocationStore trackingLocationStore;
    private final ma.mysuguclientapp.services.interfaces.OptionSelectionService optionSelectionService;

    @Override
    @Transactional(readOnly = true)
    public Page<CommandeDTO> getAllCommandes(Long clientId, Long restaurantId, StatutCommande statut, Pageable pageable) {
        Page<Commande> commandes;

        if (clientId != null && statut != null) {
            commandes = commandeRepository.findByClientIdAndStatut(clientId, statut, pageable);
        } else if (clientId != null) {
            commandes = commandeRepository.findByClientId(clientId, pageable);
        } else if (restaurantId != null && statut != null) {
            commandes = commandeRepository.findByRestaurantIdAndStatut(restaurantId, statut, pageable);
        } else if (restaurantId != null) {
            commandes = commandeRepository.findByRestaurantId(restaurantId, pageable);
        } else if (statut != null) {
            commandes = commandeRepository.findByStatut(statut, pageable);
        } else {
            commandes = commandeRepository.findAll(pageable);
        }

        return commandes.map(this::convertToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public CommandeDTO getCommandeById(Long id) {
        return convertToDTO(findCommande(id));
    }

    @Override
    @Transactional(readOnly = true)
    public CommandeDTO getCommandeByNumero(String numeroCommande) {
        Commande commande = commandeRepository.findByNumeroCommande(numeroCommande)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvee: " + numeroCommande));
        return convertToDTO(commande);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeDTO> getCommandesByClient(Long clientId) {
        return commandeRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeDTO> getCommandesByRestaurant(Long restaurantId) {
        return commandeRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeDTO> getCommandesByLivreur(Long livreurId) {
        return commandeRepository.findByLivreurIdOrderByCreatedAtDesc(livreurId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeDTO> getCommandesEnCours() {
        List<StatutCommande> statutsEnCours = Arrays.asList(
                StatutCommande.EN_ATTENTE,
                StatutCommande.CONFIRMEE,
                StatutCommande.EN_PREPARATION,
                StatutCommande.PRETE,
                StatutCommande.ASSIGNEE_LIVREUR,
                StatutCommande.EN_COURS
        );

        return commandeRepository.findByStatutInOrderByCreatedAtDesc(statutsEnCours).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Override
    public CommandeDTO createCommande(CommandeCreateDTO commandeDTO) {
        User client = userRepository.findById(commandeDTO.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client non trouve"));

        Restaurant restaurant = restaurantRepository.findById(commandeDTO.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouve"));

        if (!isRestaurantOpenNow(restaurant)) {
            throw new BadRequestException("Ce restaurant est actuellement ferme");
        }

        ModeReceptionCommande modeReception = parseModeReception(commandeDTO.getModeReception());
        if (modeReception == ModeReceptionCommande.LIVRAISON && commandeDTO.getAdresseLivraison() == null) {
            throw new BadRequestException("Une adresse de livraison est requise pour une commande en livraison");
        }

        Commande commande = new Commande();
        commande.setNumeroCommande(CommandeNumberGenerator.generate());
        commande.setClient(client);
        commande.setRestaurant(restaurant);
        commande.setStatut(StatutCommande.EN_ATTENTE);
        commande.setCommentaire(commandeDTO.getCommentaire());
        commande.setModeReception(modeReception);
        commande.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        commande.setMethodePaiement(parseMethodePaiement(commandeDTO.getMethodePaiement()));
        commande.setAdresseLivraison(toLocalisation(commandeDTO.getAdresseLivraison()));

        BigDecimal montantTotal = BigDecimal.ZERO;
        List<LigneCommande> lignes = new ArrayList<>();

        for (LigneCommandeCreateDTO ligneDTO : commandeDTO.getLignes()) {
            Plat plat = platRepository.findById(ligneDTO.getPlatId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plat non trouve: " + ligneDTO.getPlatId()));

            plat = refreshPlatAvailabilityIfNeeded(plat);
            if (!plat.getRestaurant().getId().equals(restaurant.getId())) {
                throw new BadRequestException("Tous les plats doivent provenir du meme restaurant");
            }
            if (!Boolean.TRUE.equals(plat.getIsAvailable())) {
                throw new BadRequestException("Le plat " + plat.getNom() + " n'est pas disponible");
            }

            ma.mysuguclientapp.services.interfaces.OptionSelectionService.Selection sel =
                    optionSelectionService.resolve(plat, ligneDTO.getOptionItemIds());
            java.math.BigDecimal prixUnitaireLigne = plat.getPrix().add(sel.getSupplementTotal());

            LigneCommande ligne = new LigneCommande();
            ligne.setPlat(plat);
            ligne.setQuantite(ligneDTO.getQuantite());
            ligne.setPrixUnitaire(prixUnitaireLigne);
            ligne.setMontantTotal(prixUnitaireLigne.multiply(java.math.BigDecimal.valueOf(ligneDTO.getQuantite())));
            ligne.setRemarque(ligneDTO.getRemarque());
            ligne.setCommande(commande);
            for (ma.mysuguclientapp.entities.OptionItem oi : sel.getItems()) {
                ma.mysuguclientapp.entities.LigneCommandeOption snap = new ma.mysuguclientapp.entities.LigneCommandeOption();
                snap.setLigneCommande(ligne);
                snap.setOptionItemId(oi.getId());
                snap.setOptionGroupNom(oi.getGroup() != null ? oi.getGroup().getNom() : null);
                snap.setOptionNom(oi.getNom());
                snap.setPrixSupplement(oi.getPrixSupplement());
                ligne.getOptions().add(snap);
            }
            lignes.add(ligne);
            montantTotal = montantTotal.add(ligne.getMontantTotal());
        }

        // ── Calcul des commissions par ligne ─────────────────────────────────
        ma.mysuguclientapp.entities.ParametresCaisse params = parametresCaisseRepository.findById(1L)
                .orElse(new ma.mysuguclientapp.entities.ParametresCaisse());
        BigDecimal seuilPrix = params.getSeuilPrixCommission() != null
                ? params.getSeuilPrixCommission() : new BigDecimal("10.00");
        BigDecimal commissionMinGlobal = params.getCommissionMinPourcentage() != null
                ? params.getCommissionMinPourcentage() : new BigDecimal("20.00");
        BigDecimal commissionRestaurant = restaurant.getCommissionPourcentage() != null
                ? restaurant.getCommissionPourcentage() : BigDecimal.ZERO;

        BigDecimal totalCommission = BigDecimal.ZERO;
        for (LigneCommande ligne : lignes) {
            BigDecimal tauxApplique = ligne.getPrixUnitaire().compareTo(seuilPrix) <= 0
                    ? commissionMinGlobal
                    : commissionRestaurant;
            BigDecimal commission = ligne.getMontantTotal()
                    .multiply(tauxApplique)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            ligne.setCommissionPourcentage(tauxApplique);
            ligne.setMontantCommission(commission);
            totalCommission = totalCommission.add(commission);
        }
        commande.setMontantCommissionTotal(totalCommission);
        // ─────────────────────────────────────────────────────────────────────

        // Validation de la zone de déploiement et calcul des frais
        BigDecimal fraisLivraison = BigDecimal.ZERO;
        if (modeReception == ModeReceptionCommande.LIVRAISON) {
            validerZoneDeploiement(restaurant, commande.getAdresseLivraison());
            fraisLivraison = calculerFraisAvecZone(restaurant, commande.getAdresseLivraison());
        }

        commande.setFraisLivraison(fraisLivraison);
        commande.setMontantTotal(montantTotal.add(fraisLivraison));
        commande.setLignesCommande(lignes);

        // ── Calcul des remises ────────────────────────────────────────────────
        BigDecimal remisePromotion = BigDecimal.ZERO;
        BigDecimal remiseCode = BigDecimal.ZERO;

        // 1. Promotion automatique liée au restaurant
        Promotion promoRestaurant = restaurant.getPromotion();
        if (promoRestaurant != null && Boolean.TRUE.equals(promoRestaurant.getIsActive())) {
            LocalDateTime now = LocalDateTime.now();
            boolean dateOk = (promoRestaurant.getDateDebut() == null || !now.isBefore(promoRestaurant.getDateDebut()))
                    && (promoRestaurant.getDateFin() == null || !now.isAfter(promoRestaurant.getDateFin()));
            boolean montantOk = promoRestaurant.getMontantMinCommande() == null
                    || montantTotal.compareTo(promoRestaurant.getMontantMinCommande()) >= 0;
            boolean usageOk = promoRestaurant.getUsageMax() == null
                    || promoRestaurant.getUsageCount() < promoRestaurant.getUsageMax();
            if (dateOk && montantOk && usageOk) {
                remisePromotion = montantTotal
                        .multiply(BigDecimal.valueOf(promoRestaurant.getPourcentage()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                promoRestaurant.setUsageCount(promoRestaurant.getUsageCount() + 1);
            }
        }

        // 2. Code promo saisi manuellement par le client
        String codePromoSaisi = commandeDTO.getCodePromo();
        if (codePromoSaisi != null && !codePromoSaisi.isBlank()) {
            CodePromo codePromo = codePromoRepository
                    .findValidCode(codePromoSaisi.trim().toUpperCase(), LocalDateTime.now())
                    .orElseThrow(() -> new BadRequestException("Code promo invalide ou expiré"));

            if (codePromo.getMontantMinCommande() != null
                    && montantTotal.compareTo(codePromo.getMontantMinCommande()) < 0) {
                throw new BadRequestException(
                        "Montant minimum requis pour ce code promo : " + codePromo.getMontantMinCommande() + " DH");
            }

            if (codePromo.getTypeReduction() == TypeReduction.POURCENTAGE) {
                remiseCode = montantTotal
                        .multiply(codePromo.getValeur())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (codePromo.getMontantMaxReduction() != null) {
                    remiseCode = remiseCode.min(codePromo.getMontantMaxReduction());
                }
            } else {
                remiseCode = codePromo.getValeur().min(montantTotal);
            }

            codePromo.setUsageCount(codePromo.getUsageCount() + 1);
            commande.setCodePromoUtilise(codePromo.getCode());
        }

        BigDecimal totalRemise = remisePromotion.add(remiseCode)
                .min(montantTotal); // la remise ne peut pas dépasser le sous-total plats
        BigDecimal montantFinal = commande.getMontantTotal().subtract(totalRemise).max(BigDecimal.ZERO);
        commande.setMontantRemise(totalRemise);
        commande.setMontantFinal(montantFinal);
        // ─────────────────────────────────────────────────────────────────────

        commande.setTempsLivraisonEstime(resolveTempsEstime(restaurant, lignes, modeReception));

        Commande savedCommande = commandeRepository.save(commande);
        ligneCommandeRepository.saveAll(lignes);
        log.info("Commande creee: {} pour un montant de {} (remise: {})", savedCommande.getNumeroCommande(), savedCommande.getMontantTotal(), totalRemise);
        CommandeDTO dto = convertToDTO(savedCommande);

        // Si paiement par carte bancaire, créer un PaymentIntent Stripe
        if (savedCommande.getMethodePaiement() == MethodePaiement.CARTE_BANCAIRE) {
            String clientSecret = stripeService.createPaymentIntent(
                    savedCommande.getMontantFinal() != null ? savedCommande.getMontantFinal() : savedCommande.getMontantTotal(),
                    savedCommande.getId(),
                    savedCommande.getNumeroCommande()
            );
            if (clientSecret != null) {
                String paymentIntentId = stripeService.extractPaymentIntentId(clientSecret);
                savedCommande.setStripePaymentIntentId(paymentIntentId);
                commandeRepository.save(savedCommande);
                dto.setStripeClientSecret(clientSecret);
            }
        }

        tikTakOrderIntegrationService.pushCreatedOrder(savedCommande);

        return dto;
    }

    @Override
    public CommandeDTO updateCommandeStatus(Long id, CommandeUpdateStatusDTO statusDTO) {
        Commande commande = findCommande(id);
        StatutCommande nouveauStatut = parseStatut(statusDTO.getStatut());

        validateStatusTransition(commande, nouveauStatut);
        commande.setStatut(nouveauStatut);

        if (nouveauStatut == StatutCommande.ANNULEE) {
            commande.setRaisonAnnulation(statusDTO.getRaisonAnnulation());
            // Libérer le livreur si déjà assigné
            if (commande.getLivreur() != null) {
                commande.getLivreur().setLivreurDisponible(true);
                userRepository.save(commande.getLivreur());
            }
            // Remboursement Stripe si le paiement par carte a déjà été capturé
            if (commande.getMethodePaiement() == MethodePaiement.CARTE_BANCAIRE
                    && commande.getStatutPaiement() == StatutPaiement.PAYE
                    && commande.getStripePaymentIntentId() != null) {
                try {
                    stripeService.refundPaymentIntent(commande.getStripePaymentIntentId());
                } catch (Exception e) {
                    log.warn("Erreur lors du remboursement Stripe pour la commande {}: {}",
                            commande.getNumeroCommande(), e.getMessage());
                }
            }
            commande.setStatutPaiement(StatutPaiement.REMBOURSE);
        } else if (statusDTO.getRaisonAnnulation() != null && !statusDTO.getRaisonAnnulation().isBlank()) {
            commande.setRaisonAnnulation(statusDTO.getRaisonAnnulation());
        }

        if (nouveauStatut == StatutCommande.LIVREE) {
            commande.setLivreeAt(LocalDateTime.now());
            commande.setStatutPaiement(StatutPaiement.PAYE);
            // Le livreur redevient disponible après livraison
            if (commande.getLivreur() != null) {
                commande.getLivreur().setLivreurDisponible(true);
                userRepository.save(commande.getLivreur());
                log.info("Livreur {} remis disponible après livraison de la commande {}",
                        commande.getLivreur().getEmail(), commande.getNumeroCommande());
            }
        }

        Commande updatedCommande = commandeRepository.save(commande);

        if (nouveauStatut == StatutCommande.LIVREE && updatedCommande.getMethodePaiement() == MethodePaiement.ESPECES) {
            try {
                caisseService.enregistrerCollecteClient(updatedCommande);
            } catch (Exception e) {
                log.warn("Erreur lors de l'enregistrement de la collecte caisse pour commande {}: {}", commande.getNumeroCommande(), e.getMessage());
            }
        }

        // Modèle argent legacy (shim Tiktak, techspec §7) : à chaque livraison, on enregistre
        // les gains nets du livreur (85% des frais). Alimente current_balance (retirable).
        // enregistrerGains est idempotent (OneToOne commande) et sûr si pas de livreur.
        if (nouveauStatut == StatutCommande.LIVREE && updatedCommande.getLivreur() != null) {
            try {
                gainsLivreurService.enregistrerGains(updatedCommande);
            } catch (Exception e) {
                log.warn("Erreur lors de l'enregistrement des gains livreur pour commande {}: {}", commande.getNumeroCommande(), e.getMessage());
            }
        }

        if (nouveauStatut == StatutCommande.CONFIRMEE) {
            envoyerNotificationsConfirmation(updatedCommande);
        }

        log.info("Statut de la commande {} mis a jour: {}", commande.getNumeroCommande(), nouveauStatut);
        return convertToDTO(updatedCommande);
    }

    @Override
    public CommandeDTO assignLivreur(Long commandeId, Long livreurId) {
        Commande commande = findCommande(commandeId);
        User livreur = userRepository.findById(livreurId)
                .orElseThrow(() -> new ResourceNotFoundException("Livreur non trouve"));

        if (livreur.getRole() != UserRole.LIVREUR) {
            throw new BadRequestException("L'utilisateur doit avoir le role LIVREUR");
        }
        if (resolveModeReception(commande) != ModeReceptionCommande.LIVRAISON) {
            throw new BadRequestException("Un livreur ne peut etre assigne qu'aux commandes en livraison");
        }

        // Si un autre livreur était déjà assigné, le remettre disponible
        User ancienLivreur = commande.getLivreur();
        if (ancienLivreur != null && !ancienLivreur.getId().equals(livreur.getId())) {
            ancienLivreur.setLivreurDisponible(true);
            userRepository.save(ancienLivreur);
            log.info("Ancien livreur {} libéré suite à ré-assignation de la commande {}",
                    ancienLivreur.getEmail(), commande.getNumeroCommande());
        }

        commande.setLivreur(livreur);
        livreur.setLivreurDisponible(false);
        userRepository.save(livreur);

        if (commande.getStatut() == StatutCommande.PRETE || commande.getStatut() == StatutCommande.EN_PREPARATION) {
            commande.setStatut(StatutCommande.ASSIGNEE_LIVREUR);
        }

        Commande updatedCommande = commandeRepository.save(commande);
        log.info("Livreur {} assigne a la commande {}", livreur.getNom(), commande.getNumeroCommande());

        // Notifier le livreur qu'une livraison lui est assignée
        notificationService.envoyerNotificationCommande(
                livreur.getId(),
                updatedCommande.getNumeroCommande(),
                TypeNotification.LIVREUR_ASSIGNE,
                updatedCommande.getId()
        );

        // Notifier le client qu'un livreur a été assigné à sa commande
        notificationService.envoyerNotificationCommande(
                updatedCommande.getClient().getId(),
                updatedCommande.getNumeroCommande(),
                TypeNotification.LIVREUR_ASSIGNE,
                updatedCommande.getId()
        );

        // Notifier le restaurant que la livraison est en cours
        if (updatedCommande.getRestaurant().getOwner() != null) {
            notificationService.envoyerNotificationCommande(
                    updatedCommande.getRestaurant().getOwner().getId(),
                    updatedCommande.getNumeroCommande(),
                    TypeNotification.LIVREUR_ASSIGNE,
                    updatedCommande.getId()
            );
        }

        return convertToDTO(updatedCommande);
    }

    @Override
    public CommandeDTO assignThirdPartyDelivery(Long id, AssignThirdPartyDeliveryDTO dto) {
        Commande commande = findCommande(id);

        if (dto.getNom() == null || dto.getNom().isBlank()) {
            throw new BadRequestException("Le nom du livreur tiers est requis");
        }

        // Si un livreur interne était assigné, on le libère car la livraison passe à un tiers
        if (commande.getLivreur() != null) {
            User ancienLivreur = commande.getLivreur();
            ancienLivreur.setLivreurDisponible(true);
            userRepository.save(ancienLivreur);
            commande.setLivreur(null);
        }

        commande.setLivreurTiersNom(dto.getNom());
        commande.setLivreurTiersTelephone(dto.getTelephone());
        commande.setLivreurTiersEntreprise(dto.getEntreprise());

        if (commande.getStatut() == StatutCommande.PRETE || commande.getStatut() == StatutCommande.EN_PREPARATION) {
            commande.setStatut(StatutCommande.ASSIGNEE_LIVREUR);
        }

        Commande updatedCommande = commandeRepository.save(commande);
        log.info("Livreur tiers '{}' assigne a la commande {}", dto.getNom(), commande.getNumeroCommande());

        notificationService.envoyerNotificationCommande(
                updatedCommande.getClient().getId(),
                updatedCommande.getNumeroCommande(),
                TypeNotification.LIVREUR_ASSIGNE,
                updatedCommande.getId()
        );

        return convertToDTO(updatedCommande);
    }

    @Override
    public CommandeDTO updatePaymentStatus(Long id, UpdatePaymentStatusDTO dto) {
        Commande commande = findCommande(id);

        if (dto.getStatutPaiement() == null) {
            throw new BadRequestException("Le statut de paiement est requis");
        }

        StatutPaiement ancienStatut = commande.getStatutPaiement();
        commande.setStatutPaiement(dto.getStatutPaiement());
        Commande updatedCommande = commandeRepository.save(commande);

        log.info("Statut de paiement de la commande {} mis a jour: {} -> {}",
                commande.getNumeroCommande(), ancienStatut, dto.getStatutPaiement());

        return convertToDTO(updatedCommande);
    }

    @Override
    public CommandeDTO updateDeliveryChargeAndDate(Long id, DeliveryChargeDateUpdateDTO dto) {
        Commande commande = findCommande(id);

        if (dto.getFraisLivraison() != null) {
            commande.setFraisLivraison(dto.getFraisLivraison());
        }
        if (dto.getDateLivraisonPrevue() != null) {
            commande.setDateLivraisonPrevue(dto.getDateLivraisonPrevue());
            commande.setCauseReport(dto.getCauseReport());
        }

        Commande updatedCommande = commandeRepository.save(commande);
        log.info("Frais/date de livraison mis a jour pour la commande {}", commande.getNumeroCommande());

        return convertToDTO(updatedCommande);
    }

    @Override
    public CommandeDTO uploadOrderWiseProducts(Long id, OrderWiseProductUploadDTO dto) {
        Commande commande = findCommande(id);

        if (dto.getLignes() == null || dto.getLignes().isEmpty()) {
            throw new BadRequestException("La liste des lignes livrées est requise");
        }

        for (OrderWiseProductUploadDTO.LigneLivreeDTO ligneDTO : dto.getLignes()) {
            LigneCommande ligne = ligneCommandeRepository.findById(ligneDTO.getLigneCommandeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Ligne de commande introuvable: " + ligneDTO.getLigneCommandeId()));

            if (!ligne.getCommande().getId().equals(commande.getId())) {
                throw new BadRequestException(
                        "La ligne " + ligneDTO.getLigneCommandeId() + " n'appartient pas a cette commande");
            }

            if (ligneDTO.getQuantiteLivree() != null && ligneDTO.getQuantiteLivree() < 0) {
                throw new BadRequestException("La quantite livree ne peut pas etre negative");
            }

            ligne.setQuantiteLivree(ligneDTO.getQuantiteLivree());
            ligneCommandeRepository.save(ligne);
        }

        log.info("Quantites livrees enregistrees pour la commande {}", commande.getNumeroCommande());

        return convertToDTO(findCommande(id));
    }

    @Override
    public CommandeDTO cancelCommande(Long id) {
        Commande commande = findCommande(id);

        if (commande.getStatut() == StatutCommande.LIVREE) {
            throw new BadRequestException("Impossible d'annuler une commande deja livree");
        }
        if (EnumSet.of(
                StatutCommande.EN_COURS,
                StatutCommande.ASSIGNEE_LIVREUR,
                StatutCommande.EN_PREPARATION,
                StatutCommande.PRETE
        ).contains(commande.getStatut())) {
            throw new BadRequestException("Impossible d'annuler une commande en cours de préparation ou de livraison.");
        }

        // Libérer le livreur si assigné
        if (commande.getLivreur() != null) {
            commande.getLivreur().setLivreurDisponible(true);
            userRepository.save(commande.getLivreur());
        }

        commande.setStatut(StatutCommande.ANNULEE);
        if (commande.getRaisonAnnulation() == null || commande.getRaisonAnnulation().isBlank()) {
            commande.setRaisonAnnulation("Commande annulee");
        }

        // Remboursement Stripe si le paiement par carte a déjà été capturé
        if (commande.getMethodePaiement() == MethodePaiement.CARTE_BANCAIRE
                && commande.getStatutPaiement() == StatutPaiement.PAYE
                && commande.getStripePaymentIntentId() != null) {
            try {
                stripeService.refundPaymentIntent(commande.getStripePaymentIntentId());
            } catch (Exception e) {
                log.warn("Erreur lors du remboursement Stripe pour la commande {}: {}",
                        commande.getNumeroCommande(), e.getMessage());
            }
        }

        commande.setStatutPaiement(StatutPaiement.REMBOURSE);

        Commande cancelledCommande = commandeRepository.save(commande);
        log.info("Commande {} annulee", commande.getNumeroCommande());
        return convertToDTO(cancelledCommande);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCommandeTracking(Long id) {
        Commande commande = findCommande(id);

        Map<String, Object> tracking = new HashMap<>();
        tracking.put("numeroCommande", commande.getNumeroCommande());
        tracking.put("statut", commande.getStatut().name());
        tracking.put("trackingStatut", mapTrackingStatus(commande));
        tracking.put("modeReception", resolveModeReception(commande).name());
        tracking.put("tempsEstime", commande.getTempsLivraisonEstime());
        tracking.put("createdAt", commande.getCreatedAt());
        tracking.put("raisonAnnulation", commande.getRaisonAnnulation());

        Map<String, Object> liveGps = new HashMap<>();
        var latestLocation = trackingLocationStore.getLatest(commande.getId());
        if (latestLocation != null) {
            liveGps.put("commandeId", latestLocation.getCommandeId());
            liveGps.put("livreurId", latestLocation.getLivreurId());
            liveGps.put("latitude", latestLocation.getLatitude());
            liveGps.put("longitude", latestLocation.getLongitude());
            liveGps.put("vitesse", latestLocation.getVitesse());
            liveGps.put("statut", latestLocation.getStatut());
            liveGps.put("timestamp", latestLocation.getTimestamp());
        }
        tracking.put("liveGps", liveGps.isEmpty() ? null : liveGps);

        Map<String, Object> restaurantInfo = new HashMap<>();
        restaurantInfo.put("id", commande.getRestaurant().getId());
        restaurantInfo.put("nom", commande.getRestaurant().getNom());
        restaurantInfo.put("localisation", toLocationMap(commande.getRestaurant().getLocalisation()));
        tracking.put("restaurant", restaurantInfo);

        Map<String, Object> clientInfo = new HashMap<>();
        clientInfo.put("id", commande.getClient().getId());
        clientInfo.put("nom", commande.getClient().getNom());
        clientInfo.put("prenom", commande.getClient().getPrenom());
        clientInfo.put("telephone", commande.getClient().getTelephone());
        clientInfo.put("localisation", toLocationMap(commande.getClient().getLocalisation()));
        tracking.put("client", clientInfo);

        if (commande.getLivreur() != null) {
            Map<String, Object> livreurInfo = new HashMap<>();
            livreurInfo.put("id", commande.getLivreur().getId());
            livreurInfo.put("nom", commande.getLivreur().getNom());
            livreurInfo.put("prenom", commande.getLivreur().getPrenom());
            livreurInfo.put("telephone", commande.getLivreur().getTelephone());
            livreurInfo.put("localisation", toLocationMap(commande.getLivreur().getLocalisation()));
            tracking.put("livreur", livreurInfo);
        }

        tracking.put("destination", toLocationMap(commande.getAdresseLivraison()));
        return tracking;
    }

    /**
     * Confirmation de commande :
     * 1. Tente d'auto-assigner le meilleur livreur disponible (si mode LIVRAISON)
     * 2. Envoie les notifications push appropriées selon le résultat
     */
    private void envoyerNotificationsConfirmation(Commande commande) {
        String numero = commande.getNumeroCommande();
        Long commandeId = commande.getId();
        boolean estLivraison = resolveModeReception(commande) == ModeReceptionCommande.LIVRAISON;

        // 1. Notifier le client (toujours)
        notificationService.envoyerNotificationCommande(
                commande.getClient().getId(), numero,
                TypeNotification.COMMANDE_CONFIRMEE, commandeId);

        // 2. Notifier le restaurant (toujours)
        if (commande.getRestaurant().getOwner() != null) {
            notificationService.envoyerNotification(
                    commande.getRestaurant().getOwner().getId(),
                    "Commande confirmée",
                    "La commande " + numero + " est confirmée. Veuillez la préparer.",
                    TypeNotification.COMMANDE_CONFIRMEE, commandeId, "COMMANDE");
        }

        // 3. Assignation si mode LIVRAISON.
        //    FCFS (défaut) : pas d'auto-assignation, on diffuse à tous les livreurs dispo
        //    qui revendiquent via /accept. Auto-dispatch : on assigne le meilleur livreur.
        if (estLivraison) {
            Optional<User> meilleurLivreur = autoDispatchEnabled
                    ? trouverMeilleurLivreur(commande.getRestaurant())
                    : Optional.empty();

            if (meilleurLivreur.isPresent()) {
                User livreur = meilleurLivreur.get();
                commande.setLivreur(livreur);
                livreur.setLivreurDisponible(false);
                userRepository.save(livreur);
                commandeRepository.save(commande);
                log.info("Commande {} auto-assignée au livreur {} (score optimal)",
                        numero, livreur.getEmail());

                // Notifier le livreur assigné
                notificationService.envoyerNotification(
                        livreur.getId(),
                        "Nouvelle livraison assignée",
                        "La commande " + numero + " vous a été assignée automatiquement. Préparez-vous !",
                        TypeNotification.LIVREUR_ASSIGNE, commandeId, "COMMANDE");

                // Notifier les admins de l'assignation automatique
                userRepository.findByRoleAndIsActive(UserRole.ADMIN, true)
                        .forEach(admin -> notificationService.envoyerNotification(
                                admin.getId(),
                                "Commande auto-assignée",
                                "La commande " + numero + " a été automatiquement assignée au livreur "
                                        + livreur.getPrenom() + " " + livreur.getNom() + ".",
                                TypeNotification.COMMANDE_CONFIRMEE, commandeId, "COMMANDE"));

            } else {
                // Aucun livreur disponible : broadcast + alerte admin
                log.warn("Aucun livreur disponible dans un rayon de {}km pour la commande {}",
                        Constants.AUTO_ASSIGN_RADIUS_KM, numero);

                userRepository.findByRoleAndIsActiveAndLivreurDisponible(UserRole.LIVREUR, true, true)
                        .forEach(livreur -> notificationService.envoyerNotification(
                                livreur.getId(),
                                "Nouvelle commande disponible",
                                "La commande " + numero + " est disponible pour livraison.",
                                TypeNotification.COMMANDE_CONFIRMEE, commandeId, "COMMANDE"));

                userRepository.findByRoleAndIsActive(UserRole.ADMIN, true)
                        .forEach(admin -> notificationService.envoyerNotification(
                                admin.getId(),
                                "Assignation manuelle requise",
                                "Aucun livreur disponible trouvé pour la commande " + numero
                                        + ". Assignation manuelle nécessaire.",
                                TypeNotification.COMMANDE_CONFIRMEE, commandeId, "COMMANDE"));
            }
        } else {
            // Mode RETRAIT : notifier l'admin pour suivi
            userRepository.findByRoleAndIsActive(UserRole.ADMIN, true)
                    .forEach(admin -> notificationService.envoyerNotification(
                            admin.getId(),
                            "Commande confirmée (retrait)",
                            "La commande " + numero + " est confirmée pour retrait sur place.",
                            TypeNotification.COMMANDE_CONFIRMEE, commandeId, "COMMANDE"));
        }
    }

    /**
     * Sélectionne le meilleur livreur disponible pour une commande.
     *
     * Critères de sélection :
     * - isActive = true ET livreurDisponible = true
     * - Localisation GPS connue
     * - Dans le rayon MAX ({@link Constants#AUTO_ASSIGN_RADIUS_KM} km) du restaurant
     *
     * Score composite (0-1) :
     *   score = WEIGHT_DISTANCE × (1 - distance/maxRadius) + WEIGHT_NOTE × (avgNote/5)
     * → Favorise le livreur le plus proche avec la meilleure note client.
     * → Si le livreur n'a aucun avis, on lui attribue une note neutre de 3/5.
     */
    private Optional<User> trouverMeilleurLivreur(ma.mysuguclientapp.entities.Restaurant restaurant) {
        if (restaurant.getLocalisation() == null
                || restaurant.getLocalisation().getLatitude() == null
                || restaurant.getLocalisation().getLongitude() == null) {
            log.warn("Restaurant {} sans localisation GPS : auto-assignation impossible", restaurant.getId());
            return Optional.empty();
        }

        double restLat = restaurant.getLocalisation().getLatitude();
        double restLon = restaurant.getLocalisation().getLongitude();
        double maxRadius = Constants.AUTO_ASSIGN_RADIUS_KM;

        record LivreurScore(User livreur, double score) {}

        return userRepository
                .findByRoleAndIsActiveAndLivreurDisponible(UserRole.LIVREUR, true, true)
                .stream()
                .filter(l -> l.getLocalisation() != null
                        && l.getLocalisation().getLatitude() != null
                        && l.getLocalisation().getLongitude() != null)
                .map(l -> {
                    double distance = calculateDistance(
                            restLat, restLon,
                            l.getLocalisation().getLatitude(),
                            l.getLocalisation().getLongitude());

                    if (distance > maxRadius) return new LivreurScore(l, -1); // hors rayon

                    Double avgNote = avisRepository.getAverageNoteLivreur(l.getId());
                    double note = (avgNote != null) ? avgNote : Constants.AUTO_ASSIGN_DEFAULT_NOTE;

                    double distanceScore = 1.0 - (distance / maxRadius);
                    double noteScore = note / Constants.MAX_RATING;
                    double score = Constants.AUTO_ASSIGN_WEIGHT_DISTANCE * distanceScore
                            + Constants.AUTO_ASSIGN_WEIGHT_NOTE * noteScore;

                    log.debug("Livreur {} — distance: {}km, note: {}/5, score: {}",
                            l.getEmail(),
                            Math.round(distance * 100.0) / 100.0,
                            Math.round(note * 10.0) / 10.0,
                            Math.round(score * 1000.0) / 1000.0);
                    return new LivreurScore(l, score);
                })
                .filter(ls -> ls.score() >= 0) // exclure hors rayon
                .max(Comparator.comparingDouble(LivreurScore::score))
                .map(LivreurScore::livreur);
    }

    private Commande findCommande(Long id) {
        return commandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvee"));
    }

    private void validateStatusTransition(Commande commande, StatutCommande newStatut) {
        if (commande.getStatut() == newStatut) {
            return;
        }

        Map<StatutCommande, List<StatutCommande>> validTransitions = new HashMap<>();
        validTransitions.put(StatutCommande.EN_ATTENTE, Arrays.asList(StatutCommande.CONFIRMEE, StatutCommande.ANNULEE));
        validTransitions.put(StatutCommande.CONFIRMEE, Arrays.asList(StatutCommande.EN_PREPARATION, StatutCommande.PRETE, StatutCommande.ANNULEE));
        validTransitions.put(StatutCommande.EN_PREPARATION, Arrays.asList(StatutCommande.PRETE, StatutCommande.ANNULEE));
        validTransitions.put(StatutCommande.PRETE, resolveModeReception(commande) == ModeReceptionCommande.RETRAIT_SUR_PLACE
                ? Arrays.asList(StatutCommande.LIVREE, StatutCommande.ANNULEE)
                : Arrays.asList(StatutCommande.ASSIGNEE_LIVREUR, StatutCommande.ANNULEE));
        validTransitions.put(StatutCommande.ASSIGNEE_LIVREUR, Arrays.asList(StatutCommande.EN_COURS, StatutCommande.ANNULEE));
        validTransitions.put(StatutCommande.EN_COURS, List.of(StatutCommande.LIVREE));

        List<StatutCommande> allowedTransitions = validTransitions.get(commande.getStatut());
        if (allowedTransitions == null || !allowedTransitions.contains(newStatut)) {
            throw new BadRequestException(
                    String.format("Transition de statut invalide: %s -> %s", commande.getStatut(), newStatut));
        }
    }

    private String mapTrackingStatus(Commande commande) {
        return switch (commande.getStatut()) {
            case EN_ATTENTE, CONFIRMEE -> "COMMANDE_CONFIRMEE";
            case EN_PREPARATION, PRETE -> resolveModeReception(commande) == ModeReceptionCommande.RETRAIT_SUR_PLACE
                    ? "COMMANDE_PRETE_A_RECUPERER"
                    : "EN_COURS_DE_PREPARATION";
            case ASSIGNEE_LIVREUR -> "LIVREUR_ASSIGNE";
            case EN_COURS -> "EN_COURS_DE_LIVRAISON";
            case LIVREE -> resolveModeReception(commande) == ModeReceptionCommande.RETRAIT_SUR_PLACE
                    ? "COMMANDE_RECUPEREE"
                    : "COMMANDE_LIVREE";
            case ANNULEE -> "ANNULEE";
            case NON_FINALISEE -> "NON_FINALISEE";
        };
    }

    private Map<String, Object> toLocationMap(Localisation localisation) {
        Map<String, Object> map = new HashMap<>();
        if (localisation != null) {
            map.put("latitude", localisation.getLatitude());
            map.put("longitude", localisation.getLongitude());
            map.put("adresse", localisation.getAdresse());
            map.put("ville", localisation.getVille());
            map.put("codePostal", localisation.getCodePostal());
            map.put("pays", localisation.getPays());
        }
        return map;
    }

    private Localisation toLocalisation(LocalisationDTO dto) {
        if (dto == null) {
            return null;
        }

        return Localisation.builder()
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .adresse(dto.getAdresse())
                .ville(dto.getVille())
                .codePostal(dto.getCodePostal())
                .pays(dto.getPays())
                .build();
    }

    /**
     * Vérifie que l'adresse de livraison se trouve dans la zone de déploiement
     * du restaurant. Échoue immédiatement si la zone n'est pas couverte,
     * évitant d'enregistrer une commande non livrable.
     *
     * Si le restaurant n'a pas de zone définie (ou zone désactivée),
     * aucune restriction n'est appliquée.
     */
    private void validerZoneDeploiement(Restaurant restaurant, Localisation adresse) {
        ZoneDeploiement zone = restaurant.getZoneDeploiement();

        if (zone == null || !Boolean.TRUE.equals(zone.getIsActive())) {
            return; // pas de zone configurée → aucune restriction
        }

        if (adresse == null || adresse.getLatitude() == null || adresse.getLongitude() == null) {
            throw new BadRequestException(
                    "Les coordonnées GPS de l'adresse de livraison sont requises.");
        }

        if (zone.getCentreLatitude() == null || zone.getCentreLongitude() == null || zone.getRayonKm() == null) {
            log.warn("Zone de déploiement '{}' sans coordonnées complètes — validation ignorée.", zone.getNom());
            return;
        }

        double distanceKm = calculateDistance(
                zone.getCentreLatitude(), zone.getCentreLongitude(),
                adresse.getLatitude(), adresse.getLongitude());

        if (distanceKm > zone.getRayonKm().doubleValue()) {
            throw new BadRequestException(
                    "Notre service de livraison n'est pas encore disponible dans votre zone. "
                            + "Zones couvertes actuellement : " + zone.getNom() + ".");
        }

        log.debug("Adresse validée dans la zone '{}' (distance {}km / rayon {}km)",
                zone.getNom(), String.format("%.1f", distanceKm), zone.getRayonKm());
    }

    /**
     * Calcule les frais de livraison selon la grille tarifaire de la zone :
     * - si distance ≤ distanceMinKm → fraisLivraisonMin
     * - sinon → fraisLivraisonMin + (distance - distanceMinKm) * prixExtraParKm
     *
     * Si la zone ou ses paramètres tarifaires sont absents, repli sur les
     * constantes globales (BASE_DELIVERY_FEE + distance * FEE_PER_KM).
     */
    private BigDecimal calculerFraisAvecZone(Restaurant restaurant, Localisation adresse) {
        ZoneDeploiement zone = restaurant.getZoneDeploiement();

        boolean canCalculateDistance = restaurant.getLocalisation() != null
                && adresse != null
                && restaurant.getLocalisation().getLatitude() != null
                && adresse.getLatitude() != null;

        boolean hasZoneTariff = zone != null
                && zone.getFraisLivraisonMin() != null
                && zone.getDistanceMinKm() != null
                && zone.getPrixExtraParKm() != null;

        if (canCalculateDistance && hasZoneTariff) {
            double distanceKm = calculateDistance(
                    restaurant.getLocalisation().getLatitude(),
                    restaurant.getLocalisation().getLongitude(),
                    adresse.getLatitude(), adresse.getLongitude());

            BigDecimal distance = BigDecimal.valueOf(distanceKm);
            if (distance.compareTo(zone.getDistanceMinKm()) <= 0) {
                return zone.getFraisLivraisonMin();
            } else {
                BigDecimal kmSupplementaires = distance.subtract(zone.getDistanceMinKm());
                return zone.getFraisLivraisonMin()
                        .add(kmSupplementaires.multiply(zone.getPrixExtraParKm()))
                        .setScale(0, RoundingMode.UP);
            }
        }

        // Repli : constantes globales (zone non configurée ou coordonnées manquantes)
        BigDecimal fraisCalcules;
        if (canCalculateDistance) {
            double distanceKm = calculateDistance(
                    restaurant.getLocalisation().getLatitude(),
                    restaurant.getLocalisation().getLongitude(),
                    adresse.getLatitude(), adresse.getLongitude());
            fraisCalcules = BigDecimal.valueOf(
                            Constants.BASE_DELIVERY_FEE_MAD + (distanceKm * Constants.DELIVERY_FEE_PER_KM_MAD))
                    .setScale(0, RoundingMode.UP);
        } else {
            fraisCalcules = BigDecimal.valueOf(Constants.BASE_DELIVERY_FEE_MAD);
        }

        // Plancher : fraisLivraisonMin de la zone si disponible mais sans grille complète
        if (zone != null && zone.getFraisLivraisonMin() != null) {
            fraisCalcules = fraisCalcules.max(zone.getFraisLivraisonMin());
        }

        return fraisCalcules;
    }

    private int resolveTempsEstime(Restaurant restaurant, List<LigneCommande> lignes, ModeReceptionCommande modeReception) {
        int tempsPreparation = lignes.stream()
                .map(LigneCommande::getPlat)
                .map(Plat::getTempsPreparation)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(20);

        if (modeReception == ModeReceptionCommande.RETRAIT_SUR_PLACE) {
            return tempsPreparation;
        }
        return (restaurant.getTempsLivraisonMoyen() != null ? restaurant.getTempsLivraisonMoyen() : 20) + tempsPreparation;
    }

    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 5.0;
        }

        final int r = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private Plat refreshPlatAvailabilityIfNeeded(Plat plat) {
        if (plat.getAvailabilityMode() == ModeDisponibilitePlat.INDISPONIBLE_TEMPORAIRE
                && plat.getIndisponibleJusqua() != null
                && plat.getIndisponibleJusqua().isBefore(LocalDateTime.now())) {
            plat.setAvailabilityMode(ModeDisponibilitePlat.DISPONIBLE);
            plat.setIndisponibleJusqua(null);
            plat.setIsAvailable(true);
            return platRepository.save(plat);
        }
        return plat;
    }

    private boolean isRestaurantOpenNow(Restaurant restaurant) {
        if (!Boolean.TRUE.equals(restaurant.getIsActive())) {
            return false;
        }
        if (!Boolean.TRUE.equals(restaurant.getAutoCloseEnabled())) {
            return true;
        }

        LocalTime opening = restaurant.getHeureOuverture();
        LocalTime closing = restaurant.getHeureFermeture();
        if (opening == null || closing == null || opening.equals(closing)) {
            return true;
        }

        LocalTime now = LocalTime.now();
        if (opening.isBefore(closing)) {
            return !now.isBefore(opening) && now.isBefore(closing);
        }
        return !now.isBefore(opening) || now.isBefore(closing);
    }

    private MethodePaiement parseMethodePaiement(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Methode de paiement requise");
        }
        // Accepte les libellés natifs ET les alias envoyés par les apps mobiles (6valley) :
        // paiement à la livraison -> ESPECES ; paiement en ligne/carte -> CARTE_BANCAIRE.
        String v = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        switch (v) {
            case "ESPECES", "CASH", "CASH_ON_DELIVERY", "COD" -> {
                return MethodePaiement.ESPECES;
            }
            case "CARTE_BANCAIRE", "CARTE", "CARD", "CREDIT_CARD", "DEBIT_CARD",
                 "DIGITAL_PAYMENT", "ONLINE", "ONLINE_PAYMENT", "STRIPE" -> {
                return MethodePaiement.CARTE_BANCAIRE;
            }
            default -> {
                try {
                    return MethodePaiement.valueOf(v);
                } catch (Exception e) {
                    throw new BadRequestException("Methode de paiement invalide: " + value);
                }
            }
        }
    }

    private StatutCommande parseStatut(String value) {
        try {
            return StatutCommande.valueOf(value);
        } catch (Exception e) {
            throw new BadRequestException("Statut invalide: " + value);
        }
    }

    private ModeReceptionCommande parseModeReception(String value) {
        if (value == null || value.isBlank()) {
            return ModeReceptionCommande.LIVRAISON;
        }

        try {
            return ModeReceptionCommande.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Mode de reception invalide: " + value);
        }
    }

    private ModeReceptionCommande resolveModeReception(Commande commande) {
        return commande.getModeReception() != null
                ? commande.getModeReception()
                : ModeReceptionCommande.LIVRAISON;
    }

    private CommandeDTO convertToDTO(Commande commande) {
        CommandeDTO dto = new CommandeDTO();
        dto.setId(commande.getId());
        dto.setNumeroCommande(commande.getNumeroCommande());
        dto.setStatut(commande.getStatut().name());
        dto.setTrackingStatut(mapTrackingStatus(commande));
        dto.setMontantTotal(commande.getMontantTotal());
        dto.setMontantRemise(commande.getMontantRemise());
        dto.setMontantFinal(commande.getMontantFinal() != null ? commande.getMontantFinal() : commande.getMontantTotal());
        dto.setCodePromoUtilise(commande.getCodePromoUtilise());
        dto.setFraisLivraison(commande.getFraisLivraison());
        dto.setMontantCommissionTotal(commande.getMontantCommissionTotal());
        dto.setTempsLivraisonEstime(commande.getTempsLivraisonEstime());
        dto.setCommentaire(commande.getCommentaire());
        dto.setRaisonAnnulation(commande.getRaisonAnnulation());
        dto.setModeReception(resolveModeReception(commande).name());
        dto.setCreatedAt(commande.getCreatedAt());
        dto.setUpdatedAt(commande.getUpdatedAt());
        dto.setLivreeAt(commande.getLivreeAt());
        dto.setScheduledAt(commande.getScheduledAt());
        dto.setDateLivraisonPrevue(commande.getDateLivraisonPrevue());
        dto.setCauseReport(commande.getCauseReport());
        dto.setLivreurTiersNom(commande.getLivreurTiersNom());
        dto.setLivreurTiersTelephone(commande.getLivreurTiersTelephone());
        dto.setLivreurTiersEntreprise(commande.getLivreurTiersEntreprise());

        if (commande.getMethodePaiement() != null) {
            dto.setMethodePaiement(commande.getMethodePaiement().name());
        }
        if (commande.getStatutPaiement() != null) {
            dto.setStatutPaiement(commande.getStatutPaiement().name());
        }

        if (commande.getClient() != null) {
            User client = commande.getClient();
            UserDTO clientDTO = new UserDTO();
            clientDTO.setId(client.getId());
            clientDTO.setNom(client.getNom());
            clientDTO.setPrenom(client.getPrenom());
            clientDTO.setEmail(client.getEmail());
            clientDTO.setTelephone(client.getTelephone());
            clientDTO.setAvatar(client.getAvatar());
            clientDTO.setRole(client.getRole() != null ? client.getRole().name() : null);
            clientDTO.setIsActive(client.getIsActive());
            clientDTO.setCreatedAt(client.getCreatedAt());
            clientDTO.setLocalisation(toLocalisationDTO(client.getLocalisation()));
            dto.setClient(clientDTO);
        }

        if (commande.getRestaurant() != null) {
            Restaurant restaurant = commande.getRestaurant();
            RestaurantDTO restDTO = new RestaurantDTO();
            restDTO.setId(restaurant.getId());
            restDTO.setNom(restaurant.getNom());
            restDTO.setDescription(restaurant.getDescription());
            restDTO.setLogoUrl(restaurant.getLogoUrl());
            restDTO.setAppreciation(restaurant.getAppreciation());
            restDTO.setNombreAvis(restaurant.getNombreAvis());
            restDTO.setTempsLivraisonMoyen(restaurant.getTempsLivraisonMoyen());
            restDTO.setIsActive(restaurant.getIsActive());
            restDTO.setAutoCloseEnabled(restaurant.getAutoCloseEnabled());
            restDTO.setHeureOuverture(restaurant.getHeureOuverture());
            restDTO.setHeureFermeture(restaurant.getHeureFermeture());
            restDTO.setCreatedAt(restaurant.getCreatedAt());
            restDTO.setCommissionPourcentage(restaurant.getCommissionPourcentage());
            restDTO.setLocalisation(toLocalisationDTO(restaurant.getLocalisation()));
            if (restaurant.getCategorie() != null) {
                CategorieRestaurantDTO catDTO = new CategorieRestaurantDTO();
                catDTO.setId(restaurant.getCategorie().getId());
                catDTO.setNom(restaurant.getCategorie().getNom());
                catDTO.setDescription(restaurant.getCategorie().getDescription());
                catDTO.setImageUrl(restaurant.getCategorie().getImageUrl());
                restDTO.setCategorie(catDTO);
            }
            dto.setRestaurant(restDTO);
        }

        if (commande.getLivreur() != null) {
            User livreur = commande.getLivreur();
            UserDTO livreurDTO = new UserDTO();
            livreurDTO.setId(livreur.getId());
            livreurDTO.setNom(livreur.getNom());
            livreurDTO.setPrenom(livreur.getPrenom());
            livreurDTO.setEmail(livreur.getEmail());
            livreurDTO.setTelephone(livreur.getTelephone());
            livreurDTO.setAvatar(livreur.getAvatar());
            livreurDTO.setRole(livreur.getRole() != null ? livreur.getRole().name() : null);
            livreurDTO.setIsActive(livreur.getIsActive());
            livreurDTO.setLivreurDisponible(livreur.getLivreurDisponible());
            livreurDTO.setLocalisation(toLocalisationDTO(livreur.getLocalisation()));
            dto.setLivreur(livreurDTO);
        }

        dto.setAdresseLivraison(toLocalisationDTO(commande.getAdresseLivraison()));

        if (commande.getLignesCommande() != null) {
            dto.setLignesCommande(commande.getLignesCommande().stream()
                    .map(this::convertLigneToDTO)
                    .toList());
        }

        return dto;
    }

    private LocalisationDTO toLocalisationDTO(Localisation localisation) {
        if (localisation == null) {
            return null;
        }

        LocalisationDTO dto = new LocalisationDTO();
        dto.setLatitude(localisation.getLatitude());
        dto.setLongitude(localisation.getLongitude());
        dto.setAdresse(localisation.getAdresse());
        dto.setVille(localisation.getVille());
        dto.setCodePostal(localisation.getCodePostal());
        dto.setPays(localisation.getPays());
        return dto;
    }

    private LigneCommandeDTO convertLigneToDTO(LigneCommande ligne) {
        LigneCommandeDTO dto = new LigneCommandeDTO();
        dto.setId(ligne.getId());
        dto.setQuantite(ligne.getQuantite());
        dto.setPrixUnitaire(ligne.getPrixUnitaire());
        dto.setMontantTotal(ligne.getMontantTotal());
        dto.setRemarque(ligne.getRemarque());
        dto.setQuantiteLivree(ligne.getQuantiteLivree());

        if (ligne.getPlat() != null) {
            PlatDTO platDTO = new PlatDTO();
            platDTO.setId(ligne.getPlat().getId());
            platDTO.setNom(ligne.getPlat().getNom());
            platDTO.setDescription(ligne.getPlat().getDescription());
            platDTO.setPrix(ligne.getPlat().getPrix());
            platDTO.setImageUrl(ligne.getPlat().getImageUrl());
            platDTO.setIngredients(ligne.getPlat().getIngredients());
            platDTO.setCategoriePlat(ligne.getPlat().getCategoriePlat() != null
                    ? ligne.getPlat().getCategoriePlat().name() : null);
            platDTO.setTempsPreparation(ligne.getPlat().getTempsPreparation());
            dto.setPlat(platDTO);
        }

        dto.setCommissionPourcentage(ligne.getCommissionPourcentage());
        dto.setMontantCommission(ligne.getMontantCommission());
        if (ligne.getOptions() != null) {
            dto.setOptions(ligne.getOptions().stream().map(o -> {
                ma.mysuguclientapp.dtos.OptionChoisieDTO od = new ma.mysuguclientapp.dtos.OptionChoisieDTO();
                od.setOptionItemId(o.getOptionItemId());
                od.setOptionGroupNom(o.getOptionGroupNom());
                od.setOptionNom(o.getOptionNom());
                od.setPrixSupplement(o.getPrixSupplement());
                return od;
            }).collect(java.util.stream.Collectors.toList()));
        }
        return dto;
    }
}
