package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.OptionGroupDTO;

import java.util.List;

public interface PlatOptionService {
    /** Sections d'options d'un plat (lecture publique). */
    List<OptionGroupDTO> getOptions(Long platId);

    /**
     * Remplace toute la structure d'options d'un plat (replace-all).
     * @param userEmail utilisateur authentifié — doit être ADMIN ou propriétaire du restaurant du plat.
     */
    List<OptionGroupDTO> replaceOptions(Long platId, List<OptionGroupDTO> groups, String userEmail);
}
