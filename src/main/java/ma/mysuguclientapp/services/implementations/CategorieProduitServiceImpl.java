package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CategorieProduitDTO;
import ma.mysuguclientapp.dtos.EnumOptionDTO;
import ma.mysuguclientapp.entities.CategorieProduit;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CategorieProduitRepository;
import ma.mysuguclientapp.services.interfaces.CategorieProduitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategorieProduitServiceImpl implements CategorieProduitService {

    private final CategorieProduitRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<EnumOptionDTO> listerPublic(String vertical) {
        Vertical v = parseSilencieux(vertical);
        if (v == null) {
            return List.of();
        }
        return repository.findByVerticalAndActifTrueOrderByOrdreAsc(v).stream()
                .map(c -> new EnumOptionDTO(c.getCode(), c.getLibelle()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategorieProduitDTO> listerAdmin(String vertical) {
        Vertical v = parseStrict(vertical);
        return repository.findByVerticalOrderByOrdreAsc(v).stream().map(this::toDTO).toList();
    }

    @Override
    public CategorieProduitDTO creer(CategorieProduitDTO dto) {
        Vertical v = parseStrict(dto.getVertical());
        String code = exigerCode(dto.getCode());
        if (repository.existsByVerticalAndCode(v, code)) {
            throw new BadRequestException("Ce rayon existe déjà pour cette verticale: " + code);
        }
        CategorieProduit entite = new CategorieProduit();
        entite.setVertical(v);
        entite.setCode(code);
        entite.setLibelle(exigerLibelle(dto.getLibelle()));
        entite.setOrdre(dto.getOrdre() != null ? dto.getOrdre() : 0);
        entite.setActif(dto.getActif() != null ? dto.getActif() : true);
        return toDTO(repository.save(entite));
    }

    @Override
    public CategorieProduitDTO modifier(Long id, CategorieProduitDTO dto) {
        CategorieProduit entite = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rayon non trouvé avec l'ID: " + id));
        if (dto.getLibelle() != null) {
            entite.setLibelle(exigerLibelle(dto.getLibelle()));
        }
        if (dto.getOrdre() != null) {
            entite.setOrdre(dto.getOrdre());
        }
        if (dto.getActif() != null) {
            entite.setActif(dto.getActif());
        }
        // Le code n'est pas modifiable : des lignes plats y font référence.
        return toDTO(repository.save(entite));
    }

    @Override
    public void supprimer(Long id) {
        CategorieProduit entite = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rayon non trouvé avec l'ID: " + id));
        // Suppression logique : des produits peuvent référencer ce code.
        entite.setActif(false);
        repository.save(entite);
    }

    private Vertical parseSilencieux(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Vertical.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Vertical parseStrict(String value) {
        Vertical v = parseSilencieux(value);
        if (v == null) {
            throw new BadRequestException("Vertical invalide: " + value);
        }
        return v;
    }

    private String exigerCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code du rayon est obligatoire");
        }
        return code.trim().toLowerCase();
    }

    private String exigerLibelle(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            throw new BadRequestException("Le libellé du rayon est obligatoire");
        }
        return libelle.trim();
    }

    private CategorieProduitDTO toDTO(CategorieProduit entite) {
        CategorieProduitDTO dto = new CategorieProduitDTO();
        dto.setId(entite.getId());
        dto.setVertical(entite.getVertical().name());
        dto.setCode(entite.getCode());
        dto.setLibelle(entite.getLibelle());
        dto.setOrdre(entite.getOrdre());
        dto.setActif(entite.getActif());
        return dto;
    }
}
