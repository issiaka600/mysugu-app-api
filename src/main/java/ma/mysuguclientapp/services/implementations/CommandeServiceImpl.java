package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.CommandeUpdateStatusDTO;
import ma.mysuguclientapp.dtos.LigneCommandeCreateDTO;
import ma.mysuguclientapp.dtos.LigneCommandeDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.LigneCommande;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.ModeDisponibilitePlat;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.enumerations.MethodePaiement;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.LigneCommandeRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import ma.mysuguclientapp.util.CommandeNumberGenerator;
import ma.mysuguclientapp.util.Constants;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

        BigDecimal fraisLivraison = modeReception == ModeReceptionCommande.LIVRAISON
                ? calculateFraisLivraison(restaurant.getLocalisation(), commande.getAdresseLivraison())
                : BigDecimal.ZERO;

        commande.setFraisLivraison(fraisLivraison);
        commande.setMontantTotal(montantTotal.add(fraisLivraison));
        commande.setLignesCommande(lignes);
        commande.setTempsLivraisonEstime(resolveTempsEstime(restaurant, lignes, modeReception));

        Commande savedCommande = commandeRepository.save(commande);
        ligneCommandeRepository.saveAll(lignes);
        log.info("Commande creee: {} pour un montant de {}", savedCommande.getNumeroCommande(), savedCommande.getMontantTotal());

        return convertToDTO(savedCommande);
    }

    @Override
    public CommandeDTO updateCommandeStatus(Long id, CommandeUpdateStatusDTO statusDTO) {
        Commande commande = findCommande(id);
        StatutCommande nouveauStatut = parseStatut(statusDTO.getStatut());

        validateStatusTransition(commande, nouveauStatut);
        commande.setStatut(nouveauStatut);

        if (nouveauStatut == StatutCommande.ANNULEE) {
            commande.setRaisonAnnulation(statusDTO.getRaisonAnnulation());
            commande.setStatutPaiement(StatutPaiement.REMBOURSE);
        } else if (statusDTO.getRaisonAnnulation() != null && !statusDTO.getRaisonAnnulation().isBlank()) {
            commande.setRaisonAnnulation(statusDTO.getRaisonAnnulation());
        }

        if (nouveauStatut == StatutCommande.LIVREE) {
            commande.setLivreeAt(LocalDateTime.now());
            commande.setStatutPaiement(StatutPaiement.PAYE);
        }

        Commande updatedCommande = commandeRepository.save(commande);
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

        commande.setLivreur(livreur);
        if (commande.getStatut() == StatutCommande.PRETE || commande.getStatut() == StatutCommande.EN_PREPARATION) {
            commande.setStatut(StatutCommande.EN_COURS);
        }

        Commande updatedCommande = commandeRepository.save(commande);
        log.info("Livreur {} assigne a la commande {}", livreur.getNom(), commande.getNumeroCommande());
        return convertToDTO(updatedCommande);
    }

    @Override
    public CommandeDTO cancelCommande(Long id) {
        Commande commande = findCommande(id);

        if (commande.getStatut() == StatutCommande.LIVREE) {
            throw new BadRequestException("Impossible d'annuler une commande deja livree");
        }
        if (commande.getStatut() == StatutCommande.EN_COURS) {
            throw new BadRequestException("Impossible d'annuler une commande en cours de livraison");
        }

        commande.setStatut(StatutCommande.ANNULEE);
        commande.setStatutPaiement(StatutPaiement.REMBOURSE);
        if (commande.getRaisonAnnulation() == null || commande.getRaisonAnnulation().isBlank()) {
            commande.setRaisonAnnulation("Commande annulee");
        }

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
                : Arrays.asList(StatutCommande.EN_COURS, StatutCommande.ANNULEE));
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

    private BigDecimal calculateFraisLivraison(Localisation from, Localisation to) {
        if (from == null || to == null) {
            return BigDecimal.valueOf(Constants.BASE_DELIVERY_FEE_MAD);
        }

        double distance = calculateDistance(from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude());
        return BigDecimal.valueOf(Constants.BASE_DELIVERY_FEE_MAD + (distance * Constants.DELIVERY_FEE_PER_KM_MAD))
                .setScale(0, RoundingMode.UP);
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
        try {
            return MethodePaiement.valueOf(value);
        } catch (Exception e) {
            throw new BadRequestException("Methode de paiement invalide");
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
        dto.setFraisLivraison(commande.getFraisLivraison());
        dto.setTempsLivraisonEstime(commande.getTempsLivraisonEstime());
        dto.setCommentaire(commande.getCommentaire());
        dto.setRaisonAnnulation(commande.getRaisonAnnulation());
        dto.setModeReception(resolveModeReception(commande).name());
        dto.setCreatedAt(commande.getCreatedAt());
        dto.setLivreeAt(commande.getLivreeAt());

        if (commande.getMethodePaiement() != null) {
            dto.setMethodePaiement(commande.getMethodePaiement().name());
        }
        if (commande.getStatutPaiement() != null) {
            dto.setStatutPaiement(commande.getStatutPaiement().name());
        }

        if (commande.getClient() != null) {
            UserDTO clientDTO = new UserDTO();
            clientDTO.setId(commande.getClient().getId());
            clientDTO.setNom(commande.getClient().getNom());
            clientDTO.setPrenom(commande.getClient().getPrenom());
            clientDTO.setTelephone(commande.getClient().getTelephone());
            clientDTO.setLocalisation(toLocalisationDTO(commande.getClient().getLocalisation()));
            dto.setClient(clientDTO);
        }

        if (commande.getRestaurant() != null) {
            RestaurantDTO restDTO = new RestaurantDTO();
            restDTO.setId(commande.getRestaurant().getId());
            restDTO.setNom(commande.getRestaurant().getNom());
            restDTO.setLogoUrl(commande.getRestaurant().getLogoUrl());
            restDTO.setLocalisation(toLocalisationDTO(commande.getRestaurant().getLocalisation()));
            dto.setRestaurant(restDTO);
        }

        if (commande.getLivreur() != null) {
            UserDTO livreurDTO = new UserDTO();
            livreurDTO.setId(commande.getLivreur().getId());
            livreurDTO.setNom(commande.getLivreur().getNom());
            livreurDTO.setPrenom(commande.getLivreur().getPrenom());
            livreurDTO.setTelephone(commande.getLivreur().getTelephone());
            livreurDTO.setLocalisation(toLocalisationDTO(commande.getLivreur().getLocalisation()));
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
