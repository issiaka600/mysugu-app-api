package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.TopVenteConfigDTO;
import ma.mysuguclientapp.dtos.TopVentePlatDTO;
import ma.mysuguclientapp.entities.ParametresTopVente;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.repositories.LigneCommandeRepository;
import ma.mysuguclientapp.repositories.ParametresTopVenteRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.services.interfaces.TopVenteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TopVenteServiceImpl implements TopVenteService {

    private final ParametresTopVenteRepository parametresTopVenteRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final PlatRepository platRepository;

    @Override
    @Transactional(readOnly = true)
    public int seuilVentes() {
        return parametresTopVenteRepository.findById(1L)
                .map(ParametresTopVente::getSeuilVentes)
                .orElse(5);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> ventesParPlatLivrees() {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : ligneCommandeRepository.sumQuantiteGroupByPlatByStatut(StatutCommande.LIVREE)) {
            result.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estTopVente(Long platId, Boolean manuel, Map<Long, Long> ventes) {
        if (Boolean.TRUE.equals(manuel)) {
            return true;
        }
        int seuil = seuilVentes();
        return ventes != null && ventes.getOrDefault(platId, 0L) >= seuil;
    }

    @Override
    @Transactional(readOnly = true)
    public TopVenteConfigDTO getConfig() {
        TopVenteConfigDTO dto = new TopVenteConfigDTO();
        dto.setSeuilVentes(seuilVentes());
        return dto;
    }

    @Override
    public TopVenteConfigDTO updateConfig(TopVenteConfigDTO dto) {
        if (dto == null || dto.getSeuilVentes() == null) {
            throw new BadRequestException("Le seuil de ventes est obligatoire");
        }
        int seuil = dto.getSeuilVentes();
        if (seuil < 1) {
            throw new BadRequestException("Le seuil de ventes doit être au moins 1");
        }
        ParametresTopVente params = parametresTopVenteRepository.findById(1L)
                .orElseGet(() -> ParametresTopVente.builder().id(1L).build());
        params.setSeuilVentes(seuil);
        parametresTopVenteRepository.save(params);
        log.info("Seuil Top des ventes configuré à {}", seuil);
        return getConfig();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TopVentePlatDTO> getPlatsTopVentes(Pageable pageable, Boolean topVente) {
        Page<Plat> page = platRepository.findAll(pageable);
        Map<Long, Long> ventes = ventesParPlatLivrees();
        int seuil = seuilVentes();

        java.util.List<TopVentePlatDTO> contenu = page.getContent().stream()
                .filter(plat -> topVente == null || estTopVente(plat.getId(), plat.getTopVente(), ventes) == topVente)
                .map(plat -> toTopVentePlatDTO(plat, ventes, seuil))
                .toList();

        return new PageImpl<>(contenu, pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public TopVentePlatDTO toTopVentePlatDTO(Plat plat, Map<Long, Long> ventes) {
        return toTopVentePlatDTO(plat, ventes, seuilVentes());
    }

    private TopVentePlatDTO toTopVentePlatDTO(Plat plat, Map<Long, Long> ventes, int seuil) {
        TopVentePlatDTO dto = new TopVentePlatDTO();
        dto.setId(plat.getId());
        dto.setNom(plat.getNom());
        dto.setPrix(plat.getPrix());
        if (plat.getRestaurant() != null) {
            dto.setRestaurantId(plat.getRestaurant().getId());
            dto.setRestaurantNom(plat.getRestaurant().getNom());
        }
        long qte = ventes == null ? 0L : ventes.getOrDefault(plat.getId(), 0L);
        dto.setNombreVentes(qte);
        dto.setTopVenteAuto(qte >= seuil);
        dto.setTopVenteManuel(Boolean.TRUE.equals(plat.getTopVente()));
        dto.setTopVente(dto.getTopVenteManuel() || dto.getTopVenteAuto());
        return dto;
    }
}