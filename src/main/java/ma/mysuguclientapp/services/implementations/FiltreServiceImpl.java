package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.FiltreDTO;
import ma.mysuguclientapp.entities.Filtre;
import ma.mysuguclientapp.enumerations.FiltreComportement;
import ma.mysuguclientapp.enumerations.FiltreContexte;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.FiltreRepository;
import ma.mysuguclientapp.services.interfaces.FiltreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FiltreServiceImpl implements FiltreService {

    private final FiltreRepository filtreRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FiltreDTO> getByContexte(String contexte, boolean inclureInactifs) {
        FiltreContexte ctx = parseContexte(contexte);
        List<Filtre> list = inclureInactifs
                ? filtreRepository.findByContexteOrderByOrdreAsc(ctx)
                : filtreRepository.findByContexteAndActifTrueOrderByOrdreAsc(ctx);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public FiltreDTO create(FiltreDTO dto) {
        Filtre f = new Filtre();
        applyDto(f, dto);
        return toDTO(filtreRepository.save(f));
    }

    @Override
    public FiltreDTO update(Long id, FiltreDTO dto) {
        Filtre f = filtreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Filtre non trouvé"));
        applyDto(f, dto);
        return toDTO(filtreRepository.save(f));
    }

    @Override
    public void delete(Long id) {
        if (!filtreRepository.existsById(id)) {
            throw new ResourceNotFoundException("Filtre non trouvé");
        }
        filtreRepository.deleteById(id);
    }

    private void applyDto(Filtre f, FiltreDTO dto) {
        if (dto.getContexte() != null) f.setContexte(parseContexte(dto.getContexte()));
        if (dto.getComportement() != null) f.setComportement(parseComportement(dto.getComportement()));
        if (dto.getLibelle() != null) f.setLibelle(dto.getLibelle().trim());
        f.setIcone(dto.getIcone());
        f.setCategorieId(dto.getCategorieId());
        if (dto.getOrdre() != null) f.setOrdre(dto.getOrdre());
        if (dto.getActif() != null) f.setActif(dto.getActif());
        if (f.getLibelle() == null || f.getLibelle().isBlank()) {
            throw new BadRequestException("Le libellé est obligatoire");
        }
        if (f.getComportement() == FiltreComportement.CATEGORIE && f.getCategorieId() == null) {
            throw new BadRequestException("Un filtre CATEGORIE doit référencer une catégorie");
        }
    }

    private FiltreContexte parseContexte(String s) {
        try { return FiltreContexte.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { throw new BadRequestException("Contexte invalide: " + s); }
    }

    private FiltreComportement parseComportement(String s) {
        try { return FiltreComportement.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { throw new BadRequestException("Comportement invalide: " + s); }
    }

    private FiltreDTO toDTO(Filtre f) {
        FiltreDTO dto = new FiltreDTO();
        dto.setId(f.getId());
        dto.setContexte(f.getContexte() != null ? f.getContexte().name() : null);
        dto.setComportement(f.getComportement() != null ? f.getComportement().name() : null);
        dto.setCategorieId(f.getCategorieId());
        dto.setLibelle(f.getLibelle());
        dto.setIcone(f.getIcone());
        dto.setOrdre(f.getOrdre());
        dto.setActif(f.getActif());
        return dto;
    }
}
