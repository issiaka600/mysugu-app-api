package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.AvisCreateDTO;
import ma.mysuguclientapp.dtos.AvisDTO;
import ma.mysuguclientapp.dtos.ModerationAvisDTO;
import ma.mysuguclientapp.entities.Avis;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutAvis;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.exceptions.UnauthorizedException;
import ma.mysuguclientapp.repositories.AvisRepository;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.AvisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AvisServiceImpl implements AvisService {

    private final AvisRepository avisRepository;
    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public AvisDTO soumettreAvis(String accessToken, AvisCreateDTO dto) {
        User auteur = getUserFromToken(accessToken);

        if (dto.getNoteRestaurant() == null && dto.getNoteLivreur() == null) {
            throw new BadRequestException("Au moins une note (restaurant ou livreur) est requise");
        }

        Commande commande = commandeRepository.findById(dto.getCommandeId())
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée"));

        if (!commande.getClient().getId().equals(auteur.getId())) {
            throw new UnauthorizedException("Vous ne pouvez noter que vos propres commandes");
        }
        if (commande.getStatut() != StatutCommande.LIVREE) {
            throw new BadRequestException("Vous ne pouvez noter qu'une commande livrée");
        }
        if (avisRepository.existsByCommandeId(dto.getCommandeId())) {
            throw new BadRequestException("Vous avez déjà soumis un avis pour cette commande");
        }

        Avis avis = Avis.builder()
                .commande(commande)
                .auteur(auteur)
                .restaurant(commande.getRestaurant())
                .livreur(commande.getLivreur())
                .noteRestaurant(dto.getNoteRestaurant())
                .noteLivreur(dto.getNoteLivreur())
                .commentaire(dto.getCommentaire())
                .statut(StatutAvis.EN_ATTENTE)
                .build();

        Avis saved = avisRepository.save(avis);
        log.info("Avis soumis pour la commande {} par {}", commande.getNumeroCommande(), auteur.getEmail());
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AvisDTO getAvisById(Long id) {
        return toDTO(avisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Avis non trouvé")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvisDTO> getAvisRestaurant(Long restaurantId) {
        return avisRepository.findByRestaurantIdAndStatutOrderByCreatedAtDesc(restaurantId, StatutAvis.APPROUVE)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvisDTO> getAvisLivreur(Long livreurId) {
        return avisRepository.findByLivreurIdAndStatutOrderByCreatedAtDesc(livreurId, StatutAvis.APPROUVE)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvisDTO> getMesAvis(String accessToken) {
        User user = getUserFromToken(accessToken);
        return avisRepository.findByAuteurIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public AvisDTO moderAvis(Long avisId, ModerationAvisDTO dto) {
        if (dto.getStatut() == StatutAvis.REJETE && (dto.getRaisonRejet() == null || dto.getRaisonRejet().isBlank())) {
            throw new BadRequestException("La raison du rejet est obligatoire");
        }

        Avis avis = avisRepository.findById(avisId)
                .orElseThrow(() -> new ResourceNotFoundException("Avis non trouvé"));

        avis.setStatut(dto.getStatut());
        avis.setRaisonRejet(dto.getRaisonRejet());
        Avis saved = avisRepository.save(avis);

        // Recalcule la note du restaurant
        recalculerNoteRestaurant(avis.getRestaurant().getId());

        log.info("Avis {} modéré: {}", avisId, dto.getStatut());
        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvisDTO> getAvisEnAttente() {
        return avisRepository.findByStatutOrderByCreatedAtDesc(StatutAvis.EN_ATTENTE)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public void supprimerAvis(Long avisId, String accessToken) {
        User user = getUserFromToken(accessToken);
        Avis avis = avisRepository.findById(avisId)
                .orElseThrow(() -> new ResourceNotFoundException("Avis non trouvé"));

        boolean isAdmin = user.getRole().name().equals("ADMIN");
        boolean isAuteur = avis.getAuteur().getId().equals(user.getId());

        if (!isAdmin && !isAuteur) {
            throw new UnauthorizedException("Vous n'avez pas le droit de supprimer cet avis");
        }

        Long restaurantId = avis.getRestaurant().getId();
        avisRepository.delete(avis);
        recalculerNoteRestaurant(restaurantId);
    }

    private void recalculerNoteRestaurant(Long restaurantId) {
        restaurantRepository.findById(restaurantId).ifPresent(restaurant -> {
            Double moyenne = avisRepository.getAverageNoteRestaurant(restaurantId);
            Long count = avisRepository.countApprouvesByRestaurant(restaurantId);
            restaurant.setAppreciation(moyenne != null ? Math.round(moyenne * 10.0) / 10.0 : 0.0);
            restaurant.setNombreAvis(count != null ? count.intValue() : 0);
            restaurantRepository.save(restaurant);
        });
    }

    private User getUserFromToken(String bearerToken) {
        String jwt = bearerToken != null && bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7) : bearerToken;
        String email = jwtTokenProvider.getEmailFromToken(jwt);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    private AvisDTO toDTO(Avis avis) {
        return AvisDTO.builder()
                .id(avis.getId())
                .commandeId(avis.getCommande() != null ? avis.getCommande().getId() : null)
                .auteurId(avis.getAuteur().getId())
                .auteurNom(avis.getAuteur().getNom())
                .auteurPrenom(avis.getAuteur().getPrenom())
                .auteurAvatar(avis.getAuteur().getAvatar())
                .restaurantId(avis.getRestaurant().getId())
                .restaurantNom(avis.getRestaurant().getNom())
                .livreurId(avis.getLivreur() != null ? avis.getLivreur().getId() : null)
                .livreurNom(avis.getLivreur() != null ? avis.getLivreur().getNom() : null)
                .noteRestaurant(avis.getNoteRestaurant())
                .noteLivreur(avis.getNoteLivreur())
                .commentaire(avis.getCommentaire())
                .statut(avis.getStatut().name())
                .raisonRejet(avis.getRaisonRejet())
                .createdAt(avis.getCreatedAt())
                .build();
    }
}
