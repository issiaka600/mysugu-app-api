package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.commerce.PointsFideliteDTO;
import ma.mysuguclientapp.dtos.commerce.TransactionPointsDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FideliteService {
    PointsFideliteDTO getPoints(Long userId);
    PointsFideliteDTO ajouterPoints(Long userId, Integer points, String description, String referenceCommande);
    PointsFideliteDTO utiliserPoints(Long userId, Integer points, String description);
    Page<TransactionPointsDTO> getHistoriquePoints(Long userId, Pageable pageable);
    PointsFideliteDTO calculerPointsCommande(Long userId, java.math.BigDecimal montantCommande, String referenceCommande);
}
