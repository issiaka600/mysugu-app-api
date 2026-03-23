package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.commerce.*;

import java.util.List;

public interface CodePromoService {
    CodePromoDTO creerCodePromo(CodePromoCreateDTO dto);
    CodePromoDTO getCodePromo(Long id);
    List<CodePromoDTO> getAllCodesPromo();
    CodePromoDTO activerDesactiver(Long id, boolean actif);
    void supprimerCodePromo(Long id);
    ResultatCodePromoDTO validerEtCalculer(AppliquerCodePromoDTO dto, Long userId);
}
