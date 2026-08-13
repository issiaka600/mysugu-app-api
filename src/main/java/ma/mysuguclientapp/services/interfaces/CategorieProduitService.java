package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.CategorieProduitDTO;
import ma.mysuguclientapp.dtos.EnumOptionDTO;

import java.util.List;

public interface CategorieProduitService {

    /** Lecture publique : renvoie les rayons actifs d'une verticale, vide si verticale inconnue. */
    List<EnumOptionDTO> listerPublic(String vertical);

    /** Lecture admin : tous les rayons d'une verticale, actifs ou non. */
    List<CategorieProduitDTO> listerAdmin(String vertical);

    CategorieProduitDTO creer(CategorieProduitDTO dto);

    CategorieProduitDTO modifier(Long id, CategorieProduitDTO dto);

    void supprimer(Long id);
}
