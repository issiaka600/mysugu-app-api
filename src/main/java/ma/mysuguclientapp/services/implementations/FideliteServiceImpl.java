package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.commerce.PointsFideliteDTO;
import ma.mysuguclientapp.dtos.commerce.TransactionPointsDTO;
import ma.mysuguclientapp.entities.PointsFidelite;
import ma.mysuguclientapp.entities.TransactionPoints;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.NiveauFidelite;
import ma.mysuguclientapp.enumerations.TypeTransactionPoints;
import ma.mysuguclientapp.repositories.PointsFideliteRepository;
import ma.mysuguclientapp.repositories.TransactionPointsRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.FideliteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class FideliteServiceImpl implements FideliteService {

    // 1 point for every 10 MAD spent
    private static final int POINTS_PER_10_MAD = 1;

    private final PointsFideliteRepository pointsFideliteRepository;
    private final TransactionPointsRepository transactionPointsRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional // PAS readOnly : getOrCreatePoints insère la ligne au 1er accès (sinon 500 "INSERT in read-only tx")
    public PointsFideliteDTO getPoints(Long userId) {
        PointsFidelite pf = getOrCreatePoints(userId);
        return toDTO(pf);
    }

    @Override
    @Transactional
    public PointsFideliteDTO ajouterPoints(Long userId, Integer points, String description, String referenceCommande) {
        PointsFidelite pf = getOrCreatePoints(userId);
        pf.setPointsTotal(pf.getPointsTotal() + points);
        pf.setPointsDisponibles(pf.getPointsDisponibles() + points);
        pf.setNiveauFidelite(calculerNiveau(pf.getPointsTotal()));
        pointsFideliteRepository.save(pf);

        enregistrerTransaction(pf, TypeTransactionPoints.GAIN, points, description, referenceCommande);
        return toDTO(pf);
    }

    @Override
    @Transactional
    public PointsFideliteDTO utiliserPoints(Long userId, Integer points, String description) {
        PointsFidelite pf = getOrCreatePoints(userId);
        if (pf.getPointsDisponibles() < points) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points insuffisants");
        }
        pf.setPointsDisponibles(pf.getPointsDisponibles() - points);
        pf.setPointsUtilises(pf.getPointsUtilises() + points);
        pointsFideliteRepository.save(pf);

        enregistrerTransaction(pf, TypeTransactionPoints.UTILISATION, -points, description, null);
        return toDTO(pf);
    }

    @Override
    @Transactional // PAS readOnly : getOrCreatePoints peut insérer la ligne au 1er accès
    public Page<TransactionPointsDTO> getHistoriquePoints(Long userId, Pageable pageable) {
        PointsFidelite pf = getOrCreatePoints(userId);
        return transactionPointsRepository.findByPointsFideliteIdOrderByCreatedAtDesc(pf.getId(), pageable)
                .map(this::toTransactionDTO);
    }

    @Override
    @Transactional
    public PointsFideliteDTO calculerPointsCommande(Long userId, BigDecimal montantCommande, String referenceCommande) {
        int points = montantCommande.divide(BigDecimal.TEN, 0, RoundingMode.DOWN).intValue() * POINTS_PER_10_MAD;
        if (points <= 0) return getPoints(userId);
        return ajouterPoints(userId, points, "Points gagnés sur commande " + referenceCommande, referenceCommande);
    }

    private PointsFidelite getOrCreatePoints(Long userId) {
        return pointsFideliteRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
            PointsFidelite pf = PointsFidelite.builder()
                    .user(user)
                    .pointsTotal(0)
                    .pointsDisponibles(0)
                    .pointsUtilises(0)
                    .niveauFidelite(NiveauFidelite.BRONZE)
                    .build();
            return pointsFideliteRepository.save(pf);
        });
    }

    private NiveauFidelite calculerNiveau(int pointsTotal) {
        if (pointsTotal >= 5000) return NiveauFidelite.PLATINE;
        if (pointsTotal >= 2000) return NiveauFidelite.OR;
        if (pointsTotal >= 500) return NiveauFidelite.ARGENT;
        return NiveauFidelite.BRONZE;
    }

    private void enregistrerTransaction(PointsFidelite pf, TypeTransactionPoints type, Integer points,
                                         String description, String referenceCommande) {
        TransactionPoints tx = TransactionPoints.builder()
                .pointsFidelite(pf)
                .type(type)
                .points(points)
                .description(description)
                .referenceCommande(referenceCommande)
                .build();
        transactionPointsRepository.save(tx);
    }

    private PointsFideliteDTO toDTO(PointsFidelite pf) {
        PointsFideliteDTO dto = new PointsFideliteDTO();
        dto.setId(pf.getId());
        dto.setPointsTotal(pf.getPointsTotal());
        dto.setPointsDisponibles(pf.getPointsDisponibles());
        dto.setPointsUtilises(pf.getPointsUtilises());
        dto.setNiveauFidelite(pf.getNiveauFidelite());
        if (pf.getUser() != null) {
            dto.setUserId(pf.getUser().getId());
            dto.setUserNom(pf.getUser().getNom());
            dto.setUserPrenom(pf.getUser().getPrenom());
        }
        // Calculate points needed for next level
        int total = pf.getPointsTotal();
        NiveauFidelite niveau = pf.getNiveauFidelite();
        if (niveau == NiveauFidelite.BRONZE) {
            dto.setPointsPourProchainNiveau(500 - total);
            dto.setProchainNiveau("ARGENT");
        } else if (niveau == NiveauFidelite.ARGENT) {
            dto.setPointsPourProchainNiveau(2000 - total);
            dto.setProchainNiveau("OR");
        } else if (niveau == NiveauFidelite.OR) {
            dto.setPointsPourProchainNiveau(5000 - total);
            dto.setProchainNiveau("PLATINE");
        } else {
            dto.setPointsPourProchainNiveau(0);
            dto.setProchainNiveau("MAX");
        }
        return dto;
    }

    private TransactionPointsDTO toTransactionDTO(TransactionPoints tx) {
        TransactionPointsDTO dto = new TransactionPointsDTO();
        dto.setId(tx.getId());
        dto.setType(tx.getType());
        dto.setPoints(tx.getPoints());
        dto.setDescription(tx.getDescription());
        dto.setReferenceCommande(tx.getReferenceCommande());
        dto.setCreatedAt(tx.getCreatedAt());
        return dto;
    }
}
