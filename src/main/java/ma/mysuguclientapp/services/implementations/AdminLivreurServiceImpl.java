package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.admin.LivreurDetailDTO;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutRetrait;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.AvisRepository;
import ma.mysuguclientapp.repositories.CaisseLivreurRepository;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.DemandeRetraitRepository;
import ma.mysuguclientapp.repositories.GainsLivreurRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.AdminLivreurService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminLivreurServiceImpl implements AdminLivreurService {

    private static final List<StatutCommande> STATUTS_EN_COURS =
            List.of(StatutCommande.ASSIGNEE_LIVREUR, StatutCommande.EN_COURS);

    private final UserRepository userRepository;
    private final CommandeRepository commandeRepository;
    private final AvisRepository avisRepository;
    private final GainsLivreurRepository gainsLivreurRepository;
    private final CaisseLivreurRepository caisseLivreurRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;

    @Override
    @Transactional(readOnly = true)
    public LivreurDetailDTO getLivreurDetails(Long id) {
        User livreur = findLivreur(id);

        LivreurDetailDTO dto = new LivreurDetailDTO();
        dto.setId(livreur.getId());
        dto.setNom(livreur.getNom());
        dto.setPrenom(livreur.getPrenom());
        dto.setEmail(livreur.getEmail());
        dto.setTelephone(livreur.getTelephone());
        dto.setAvatar(livreur.getAvatar());
        dto.setIsActive(livreur.getIsActive());
        dto.setLivreurDisponible(livreur.getLivreurDisponible());
        dto.setCreatedAt(livreur.getCreatedAt());

        dto.setNombreLivraisons(commandeRepository.countByLivreurIdAndStatut(id, StatutCommande.LIVREE));
        dto.setCommandesEnCours(commandeRepository.countByLivreurIdAndStatutIn(id, STATUTS_EN_COURS));
        dto.setNoteMoyenne(avisRepository.getAverageNoteLivreur(id));

        BigDecimal gainsNets = gainsLivreurRepository.sumMontantNetByLivreur(id);
        BigDecimal totalRetireApprouve = demandeRetraitRepository.sumMontantByLivreurAndStatut(id, StatutRetrait.APPROUVE);
        BigDecimal enAttenteRetrait = demandeRetraitRepository.sumMontantByLivreurAndStatut(id, StatutRetrait.EN_ATTENTE);

        dto.setSoldeActuel(gainsNets.subtract(totalRetireApprouve));
        dto.setTotalRetireApprouve(totalRetireApprouve);
        dto.setMontantEnAttenteRetrait(enAttenteRetrait);
        dto.setEspeceEnCaisse(caisseLivreurRepository.findByLivreurId(id)
                .map(c -> c.getSoldeCourant())
                .orElse(BigDecimal.ZERO));

        return dto;
    }

    @Override
    public void deleteLivreur(Long id) {
        User livreur = findLivreur(id);

        long commandesEnCours = commandeRepository.countByLivreurIdAndStatutIn(id, STATUTS_EN_COURS);
        if (commandesEnCours > 0) {
            throw new BadRequestException(
                    "Impossible de supprimer ce livreur : il a " + commandesEnCours + " livraison(s) en cours.");
        }

        // Soft-delete (même convention que la suppression de compte self-service)
        livreur.setIsDeleted(true);
        livreur.setDeletedAt(LocalDateTime.now());
        livreur.setIsActive(false);
        livreur.setLivreurDisponible(false);
        userRepository.save(livreur);

        log.info("Livreur {} (id={}) supprime (soft-delete) par l'admin", livreur.getEmail(), id);
    }

    private User findLivreur(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Livreur non trouve avec l'ID: " + id));
        if (user.getRole() != UserRole.LIVREUR) {
            throw new BadRequestException("L'utilisateur " + id + " n'est pas un livreur");
        }
        return user;
    }
}