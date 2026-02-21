package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.*;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.MethodePaiement;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import ma.mysuguclientapp.util.CommandeNumberGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    @Transactional(readOnly = true)
    public Page<CommandeDTO> getAllCommandes(Long clientId, Long restaurantId,
                                             StatutCommande statut, Pageable pageable) {
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
        Commande commande = commandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée avec l'ID: " + id));
        return convertToDTO(commande);
    }

    @Override
    @Transactional(readOnly = true)
    public CommandeDTO getCommandeByNumero(String numeroCommande) {
        Commande commande = commandeRepository.findByNumeroCommande(numeroCommande)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée: " + numeroCommande));
        return convertToDTO(commande);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeDTO> getCommandesByClient(Long clientId) {
        List<Commande> commandes = commandeRepository.findByClientIdOrderByCreatedAtDesc(clientId);
        return commandes.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeDTO> getCommandesByRestaurant(Long restaurantId) {
        List<Commande> commandes = commandeRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        return commandes.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeDTO> getCommandesByLivreur(Long livreurId) {
        List<Commande> commandes = commandeRepository.findByLivreurIdOrderByCreatedAtDesc(livreurId);
        return commandes.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeDTO> getCommandesEnCours() {
        List<StatutCommande> statutsEnCours = Arrays.asList(
                StatutCommande.EN_ATTENTE,
                StatutCommande.CONFIRMEE,
                StatutCommande.EN_PREPARATION,
                StatutCommande.EN_COURS
        );

        List<Commande> commandes = commandeRepository.findByStatutInOrderByCreatedAtDesc(statutsEnCours);
        return commandes.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public CommandeDTO createCommande(CommandeCreateDTO commandeDTO) {
        // Vérifier le client
        User client = userRepository.findById(commandeDTO.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client non trouvé"));

        // Vérifier le restaurant
        Restaurant restaurant = restaurantRepository.findById(commandeDTO.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));

        if (!restaurant.getIsActive()) {
            throw new BadRequestException("Ce restaurant est actuellement fermé");
        }

        // Créer la commande
        Commande commande = new Commande();
        commande.setNumeroCommande(CommandeNumberGenerator.generate());
        commande.setClient(client);
        commande.setRestaurant(restaurant);
        commande.setStatut(StatutCommande.EN_ATTENTE);
        commande.setCommentaire(commandeDTO.getCommentaire());

        // Méthode de paiement
        try {
            commande.setMethodePaiement(MethodePaiement.valueOf(commandeDTO.getMethodePaiement()));
            commande.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Méthode de paiement invalide");
        }

        // Adresse de livraison
        if (commandeDTO.getAdresseLivraison() != null) {
            Localisation adresse = new Localisation();
            adresse.setLatitude(commandeDTO.getAdresseLivraison().getLatitude());
            adresse.setLongitude(commandeDTO.getAdresseLivraison().getLongitude());
            adresse.setAdresse(commandeDTO.getAdresseLivraison().getAdresse());
            adresse.setVille(commandeDTO.getAdresseLivraison().getVille());
            adresse.setCodePostal(commandeDTO.getAdresseLivraison().getCodePostal());
            adresse.setPays(commandeDTO.getAdresseLivraison().getPays());
            commande.setAdresseLivraison(adresse);
        }

        // Calculer le montant total et créer les lignes de commande
        BigDecimal montantTotal = BigDecimal.ZERO;
        List<LigneCommande> lignes = new ArrayList<>();

        for (LigneCommandeCreateDTO ligneDTO : commandeDTO.getLignes()) {
            Plat plat = platRepository.findById(ligneDTO.getPlatId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé: " + ligneDTO.getPlatId()));

            if (!plat.getIsAvailable()) {
                throw new BadRequestException("Le plat " + plat.getNom() + " n'est pas disponible");
            }

            if (!plat.getRestaurant().getId().equals(restaurant.getId())) {
                throw new BadRequestException("Tous les plats doivent provenir du même restaurant");
            }

            LigneCommande ligne = new LigneCommande();
            ligne.setPlat(plat);
            ligne.setQuantite(ligneDTO.getQuantite());
            ligne.setPrixUnitaire(plat.getPrix());
            ligne.setMontantTotal(plat.getPrix().multiply(BigDecimal.valueOf(ligneDTO.getQuantite())));
            ligne.setRemarque(ligneDTO.getRemarque());
            ligne.setCommande(commande);

            lignes.add(ligne);
            montantTotal = montantTotal.add(ligne.getMontantTotal());
        }

        // Calculer frais de livraison (exemple simple)
        BigDecimal fraisLivraison = calculateFraisLivraison(
                restaurant.getLocalisation(),
                commande.getAdresseLivraison()
        );
        commande.setFraisLivraison(fraisLivraison);
        montantTotal = montantTotal.add(fraisLivraison);

        commande.setMontantTotal(montantTotal);
        commande.setLignesCommande(lignes);

        // Estimer le temps de livraison
        Integer tempsEstime = restaurant.getTempsLivraisonMoyen();
        commande.setTempsLivraisonEstime(tempsEstime);

        Commande savedCommande = commandeRepository.save(commande);
        log.info("Commande créée: {} pour un montant de {}", savedCommande.getNumeroCommande(), montantTotal);

        return convertToDTO(savedCommande);
    }

    @Override
    public CommandeDTO updateCommandeStatus(Long id, CommandeUpdateStatusDTO statusDTO) {
        Commande commande = commandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));

        StatutCommande nouveauStatut;
        try {
            nouveauStatut = StatutCommande.valueOf(statusDTO.getStatut());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Statut invalide: " + statusDTO.getStatut());
        }

        // Valider la transition de statut
        validateStatusTransition(commande.getStatut(), nouveauStatut);

        commande.setStatut(nouveauStatut);

        // Si la commande est livrée, enregistrer la date
        if (nouveauStatut == StatutCommande.LIVREE) {
            commande.setLivreeAt(LocalDateTime.now());
            commande.setStatutPaiement(StatutPaiement.PAYE);
        }

        Commande updatedCommande = commandeRepository.save(commande);
        log.info("Statut de la commande {} mis à jour: {}", commande.getNumeroCommande(), nouveauStatut);

        return convertToDTO(updatedCommande);
    }

    @Override
    public CommandeDTO assignLivreur(Long commandeId, Long livreurId) {
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));

        User livreur = userRepository.findById(livreurId)
                .orElseThrow(() -> new ResourceNotFoundException("Livreur non trouvé"));

        if (livreur.getRole() != UserRole.LIVREUR) {
            throw new BadRequestException("L'utilisateur doit avoir le rôle LIVREUR");
        }

        commande.setLivreur(livreur);

        // Si la commande est en préparation, passer en cours
        if (commande.getStatut() == StatutCommande.EN_PREPARATION) {
            commande.setStatut(StatutCommande.EN_COURS);
        }

        Commande updatedCommande = commandeRepository.save(commande);
        log.info("Livreur {} assigné à la commande {}", livreur.getNom(), commande.getNumeroCommande());

        return convertToDTO(updatedCommande);
    }

    @Override
    public CommandeDTO cancelCommande(Long id) {
        Commande commande = commandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));

        // Vérifier si la commande peut être annulée
        if (commande.getStatut() == StatutCommande.LIVREE) {
            throw new BadRequestException("Impossible d'annuler une commande déjà livrée");
        }

        if (commande.getStatut() == StatutCommande.EN_COURS) {
            throw new BadRequestException("Impossible d'annuler une commande en cours de livraison");
        }

        commande.setStatut(StatutCommande.ANNULEE);
        commande.setStatutPaiement(StatutPaiement.REMBOURSE);

        Commande cancelledCommande = commandeRepository.save(commande);
        log.info("Commande {} annulée", commande.getNumeroCommande());

        return convertToDTO(cancelledCommande);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCommandeTracking(Long id) {
        Commande commande = commandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));

        Map<String, Object> tracking = new HashMap<>();
        tracking.put("numeroCommande", commande.getNumeroCommande());
        tracking.put("statut", commande.getStatut().name());
        tracking.put("restaurant", commande.getRestaurant().getNom());
        tracking.put("tempsEstime", commande.getTempsLivraisonEstime());

        if (commande.getLivreur() != null) {
            Map<String, Object> livreurInfo = new HashMap<>();
            livreurInfo.put("nom", commande.getLivreur().getNom());
            livreurInfo.put("prenom", commande.getLivreur().getPrenom());
            livreurInfo.put("telephone", commande.getLivreur().getTelephone());

            if (commande.getLivreur().getLocalisation() != null) {
                livreurInfo.put("latitude", commande.getLivreur().getLocalisation().getLatitude());
                livreurInfo.put("longitude", commande.getLivreur().getLocalisation().getLongitude());
            }

            tracking.put("livreur", livreurInfo);
        }

        if (commande.getAdresseLivraison() != null) {
            Map<String, Object> adresse = new HashMap<>();
            adresse.put("latitude", commande.getAdresseLivraison().getLatitude());
            adresse.put("longitude", commande.getAdresseLivraison().getLongitude());
            adresse.put("adresse", commande.getAdresseLivraison().getAdresse());
            tracking.put("destination", adresse);
        }

        tracking.put("createdAt", commande.getCreatedAt());

        return tracking;
    }

    // ========== MÉTHODES UTILITAIRES ==========

    private void validateStatusTransition(StatutCommande currentStatut, StatutCommande newStatut) {
        // Logique de validation des transitions de statut
        Map<StatutCommande, List<StatutCommande>> validTransitions = new HashMap<>();

        validTransitions.put(StatutCommande.EN_ATTENTE,
                Arrays.asList(StatutCommande.CONFIRMEE, StatutCommande.ANNULEE));
        validTransitions.put(StatutCommande.CONFIRMEE,
                Arrays.asList(StatutCommande.EN_PREPARATION, StatutCommande.ANNULEE));
        validTransitions.put(StatutCommande.EN_PREPARATION,
                Arrays.asList(StatutCommande.EN_COURS, StatutCommande.ANNULEE));
        validTransitions.put(StatutCommande.EN_COURS,
                Arrays.asList(StatutCommande.LIVREE));

        List<StatutCommande> allowedTransitions = validTransitions.get(currentStatut);
        if (allowedTransitions == null || !allowedTransitions.contains(newStatut)) {
            throw new BadRequestException(
                    String.format("Transition de statut invalide: %s -> %s", currentStatut, newStatut));
        }
    }

    private BigDecimal calculateFraisLivraison(Localisation from, Localisation to) {
        if (from == null || to == null) {
            return BigDecimal.valueOf(2000); // Frais par défaut
        }

        double distance = calculateDistance(
                from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude()
        );

        // 500 FCFA de base + 200 FCFA par km
        BigDecimal frais = BigDecimal.valueOf(500)
                .add(BigDecimal.valueOf(distance * 200));

        return frais.setScale(0, BigDecimal.ROUND_UP);
    }

    private double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 5.0; // Distance par défaut
        }

        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private CommandeDTO convertToDTO(Commande commande) {
        CommandeDTO dto = new CommandeDTO();
        dto.setId(commande.getId());
        dto.setNumeroCommande(commande.getNumeroCommande());
        dto.setStatut(commande.getStatut().name());
        dto.setMontantTotal(commande.getMontantTotal());
        dto.setFraisLivraison(commande.getFraisLivraison());
        dto.setTempsLivraisonEstime(commande.getTempsLivraisonEstime());
        dto.setCommentaire(commande.getCommentaire());
        dto.setCreatedAt(commande.getCreatedAt());
        dto.setLivreeAt(commande.getLivreeAt());

        if (commande.getMethodePaiement() != null) {
            dto.setMethodePaiement(commande.getMethodePaiement().name());
        }
        if (commande.getStatutPaiement() != null) {
            dto.setStatutPaiement(commande.getStatutPaiement().name());
        }

        // Client (simplifié)
        if (commande.getClient() != null) {
            UserDTO clientDTO = new UserDTO();
            clientDTO.setId(commande.getClient().getId());
            clientDTO.setNom(commande.getClient().getNom());
            clientDTO.setPrenom(commande.getClient().getPrenom());
            clientDTO.setTelephone(commande.getClient().getTelephone());
            dto.setClient(clientDTO);
        }

        // Restaurant (simplifié)
        if (commande.getRestaurant() != null) {
            RestaurantDTO restDTO = new RestaurantDTO();
            restDTO.setId(commande.getRestaurant().getId());
            restDTO.setNom(commande.getRestaurant().getNom());
            restDTO.setLogoUrl(commande.getRestaurant().getLogoUrl());
            dto.setRestaurant(restDTO);
        }

        // Livreur
        if (commande.getLivreur() != null) {
            UserDTO livreurDTO = new UserDTO();
            livreurDTO.setId(commande.getLivreur().getId());
            livreurDTO.setNom(commande.getLivreur().getNom());
            livreurDTO.setPrenom(commande.getLivreur().getPrenom());
            livreurDTO.setTelephone(commande.getLivreur().getTelephone());
            dto.setLivreur(livreurDTO);
        }

        // Adresse de livraison
        if (commande.getAdresseLivraison() != null) {
            LocalisationDTO locDTO = new LocalisationDTO();
            locDTO.setLatitude(commande.getAdresseLivraison().getLatitude());
            locDTO.setLongitude(commande.getAdresseLivraison().getLongitude());
            locDTO.setAdresse(commande.getAdresseLivraison().getAdresse());
            locDTO.setVille(commande.getAdresseLivraison().getVille());
            dto.setAdresseLivraison(locDTO);
        }

        // Lignes de commande
        if (commande.getLignesCommande() != null) {
            List<LigneCommandeDTO> lignesDTO = commande.getLignesCommande().stream()
                    .map(this::convertLigneToDTO)
                    .collect(Collectors.toList());
            dto.setLignesCommande(lignesDTO);
        }

        return dto;
    }

    private LigneCommandeDTO convertLigneToDTO(LigneCommande ligne) {
        LigneCommandeDTO dto = new LigneCommandeDTO();
        dto.setId(ligne.getId());
        dto.setQuantite(ligne.getQuantite());
        dto.setPrixUnitaire(ligne.getPrixUnitaire());
        dto.setMontantTotal(ligne.getMontantTotal());
        dto.setRemarque(ligne.getRemarque());

        if (ligne.getPlat() != null) {
            PlatDTO platDTO = new PlatDTO();
            platDTO.setId(ligne.getPlat().getId());
            platDTO.setNom(ligne.getPlat().getNom());
            platDTO.setImageUrl(ligne.getPlat().getImageUrl());
            platDTO.setPrix(ligne.getPlat().getPrix());
            dto.setPlat(platDTO);
        }

        return dto;
    }
}
