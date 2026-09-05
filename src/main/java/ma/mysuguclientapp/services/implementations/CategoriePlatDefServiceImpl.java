package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.CategoriePlatDefDTO;
import ma.mysuguclientapp.dtos.EnumOptionDTO;
import ma.mysuguclientapp.entities.CategoriePlatDef;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CategoriePlatDefRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.services.interfaces.CategoriePlatDefService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoriePlatDefServiceImpl implements CategoriePlatDefService {

    private final CategoriePlatDefRepository repository;
    private final PlatRepository platRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EnumOptionDTO> listerPublic() {
        return repository.findByActifTrueOrderByOrdreAsc().stream()
                .map(c -> new EnumOptionDTO(c.getCode(), c.getLibelle()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriePlatDefDTO> listerAdmin() {
        Map<String, Long> counts = comptesParCode();
        return repository.findAllByOrderByOrdreAsc().stream()
                .map(c -> toDTO(c, counts.getOrDefault(c.getCode(), 0L)))
                .toList();
    }

    @Override
    public CategoriePlatDefDTO creer(CategoriePlatDefDTO dto) {
        String code = exigerCode(dto.getCode());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("Cette catégorie de plat existe déjà: " + code);
        }
        CategoriePlatDef entite = new CategoriePlatDef();
        entite.setCode(code);
        entite.setLibelle(exigerLibelle(dto.getLibelle()));
        entite.setOrdre(dto.getOrdre() != null ? dto.getOrdre() : prochainOrdre());
        entite.setActif(dto.getActif() != null ? dto.getActif() : true);
        entite.setIcone(dto.getIcone());
        CategoriePlatDef sauve = repository.save(entite);
        log.info("Catégorie de plat créée: {} ({})", sauve.getLibelle(), sauve.getCode());
        return toDTO(sauve, 0L);
    }

    @Override
    public CategoriePlatDefDTO modifier(Long id, CategoriePlatDefDTO dto) {
        CategoriePlatDef entite = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie de plat non trouvée avec l'ID: " + id));
        if (dto.getLibelle() != null) {
            entite.setLibelle(exigerLibelle(dto.getLibelle()));
        }
        if (dto.getOrdre() != null) {
            entite.setOrdre(dto.getOrdre());
        }
        if (dto.getActif() != null) {
            entite.setActif(dto.getActif());
        }
        if (dto.getIcone() != null) {
            entite.setIcone(dto.getIcone());
        }
        // Le code n'est pas modifiable : Plat.categoriePlat y fait référence.
        CategoriePlatDef sauve = repository.save(entite);
        return toDTO(sauve, comptesParCode().getOrDefault(sauve.getCode(), 0L));
    }

    @Override
    public CategoriePlatDefDTO activerDesactiver(Long id, boolean actif) {
        CategoriePlatDef entite = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie de plat non trouvée avec l'ID: " + id));
        entite.setActif(actif);
        CategoriePlatDef sauve = repository.save(entite);
        return toDTO(sauve, comptesParCode().getOrDefault(sauve.getCode(), 0L));
    }

    @Override
    public void supprimer(Long id) {
        CategoriePlatDef entite = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie de plat non trouvée avec l'ID: " + id));
        Long nbPlats = comptesParCode().getOrDefault(entite.getCode(), 0L);
        if (nbPlats > 0) {
            throw new BadRequestException("Impossible de supprimer cette catégorie : " + nbPlats
                    + " plat(s) y sont encore liés. Désactivez-la à la place.");
        }
        repository.delete(entite);
        log.info("Catégorie de plat supprimée: {} ({})", entite.getLibelle(), entite.getCode());
    }

    private Map<String, Long> comptesParCode() {
        return platRepository.countPlatsParCategoriePlat().stream()
                .collect(Collectors.toMap(row -> (String) row[0],
                        row -> (Long) row[1],
                        Long::sum));
    }

    private int prochainOrdre() {
        return repository.findAllByOrderByOrdreAsc().stream()
                .mapToInt(CategoriePlatDef::getOrdre)
                .max().orElse(-1) + 1;
    }

    private String exigerCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Le code de la catégorie est obligatoire");
        }
        return value.trim().toUpperCase();
    }

    private String exigerLibelle(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Le libellé de la catégorie est obligatoire");
        }
        return value.trim();
    }

    private CategoriePlatDefDTO toDTO(CategoriePlatDef entite, Long nombrePlats) {
        CategoriePlatDefDTO dto = new CategoriePlatDefDTO();
        dto.setId(entite.getId());
        dto.setCode(entite.getCode());
        dto.setLibelle(entite.getLibelle());
        dto.setOrdre(entite.getOrdre());
        dto.setActif(entite.getActif());
        dto.setIcone(entite.getIcone());
        dto.setNombrePlats(nombrePlats);
        return dto;
    }
}