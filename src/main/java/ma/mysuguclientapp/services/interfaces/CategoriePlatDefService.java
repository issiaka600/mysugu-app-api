package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.CategoriePlatDefDTO;
import ma.mysuguclientapp.dtos.EnumOptionDTO;

import java.util.List;

public interface CategoriePlatDefService {

    /** Lecture publique (client + select restaurateur) : catégories actives triées par ordre. */
    List<EnumOptionDTO> listerPublic();

    /** Lecture admin : toutes les catégories, actives ou non, avec le nombre de plats associés. */
    List<CategoriePlatDefDTO> listerAdmin();

    CategoriePlatDefDTO creer(CategoriePlatDefDTO dto);

    CategoriePlatDefDTO modifier(Long id, CategoriePlatDefDTO dto);

    CategoriePlatDefDTO activerDesactiver(Long id, boolean actif);

    void supprimer(Long id);
}