package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.commerce.*;

import java.util.List;

public interface CodePromoService {
    CodePromoDTO creerCodePromo(CodePromoCreateDTO dto);
    CodePromoDTO getCodePromo(Long id);
    List<CodePromoDTO> getAllCodesPromo();
    List<CodePromoDTO> getCodesPromoActifs();

    /**
     * Édite un coupon existant (BUILD-MINIMAL — aucun update natif avant la tranche 3h). Préserve
     * usageCount/createdBy/isActive/createdAt ; conflit (409) uniquement si le nouveau code
     * appartient à une AUTRE ligne. Plan 3h.1.
     */
    CodePromoDTO mettreAJour(Long id, CodePromoCreateDTO dto);

    CodePromoDTO activerDesactiver(Long id, boolean actif);
    void supprimerCodePromo(Long id);
    ResultatCodePromoDTO validerEtCalculer(AppliquerCodePromoDTO dto, Long userId);
}
