package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.admin.LivreurDetailDTO;

public interface AdminLivreurService {
    LivreurDetailDTO getLivreurDetails(Long id);
    void deleteLivreur(Long id);
}