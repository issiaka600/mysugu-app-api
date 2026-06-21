package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.FiltreDTO;

import java.util.List;

public interface FiltreService {
    List<FiltreDTO> getByContexte(String contexte, boolean inclureInactifs);
    FiltreDTO create(FiltreDTO dto);
    FiltreDTO update(Long id, FiltreDTO dto);
    void delete(Long id);
}
