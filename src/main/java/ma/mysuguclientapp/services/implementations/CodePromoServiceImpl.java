package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.commerce.*;
import ma.mysuguclientapp.entities.CodePromo;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.TypeReduction;
import ma.mysuguclientapp.repositories.CodePromoRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.CodePromoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodePromoServiceImpl implements CodePromoService {

    private final CodePromoRepository codePromoRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CodePromoDTO creerCodePromo(CodePromoCreateDTO dto) {
        if (codePromoRepository.existsByCode(dto.getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Code promo déjà existant: " + dto.getCode());
        }
        CodePromo promo = CodePromo.builder()
                .code(dto.getCode().toUpperCase())
                .description(dto.getDescription())
                .typeReduction(dto.getTypeReduction())
                .valeur(dto.getValeur())
                .montantMinCommande(dto.getMontantMinCommande())
                .montantMaxReduction(dto.getMontantMaxReduction())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .usageMax(dto.getUsageMax())
                .usageCount(0)
                .isActive(true)
                .build();
        return toDTO(codePromoRepository.save(promo));
    }

    @Override
    @Transactional(readOnly = true)
    public CodePromoDTO getCodePromo(Long id) {
        return toDTO(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodePromoDTO> getAllCodesPromo() {
        return codePromoRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodePromoDTO> getCodesPromoActifs() {
        LocalDateTime now = LocalDateTime.now();
        return codePromoRepository.findByIsActiveTrue().stream()
                .filter(promo -> promo.getDateDebut() == null || !promo.getDateDebut().isAfter(now))
                .filter(promo -> promo.getDateFin() == null || !promo.getDateFin().isBefore(now))
                .filter(promo -> promo.getUsageMax() == null ||
                        promo.getUsageCount() == null ||
                        promo.getUsageCount() < promo.getUsageMax())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CodePromoDTO activerDesactiver(Long id, boolean actif) {
        CodePromo promo = findById(id);
        promo.setIsActive(actif);
        return toDTO(codePromoRepository.save(promo));
    }

    @Override
    @Transactional
    public void supprimerCodePromo(Long id) {
        codePromoRepository.delete(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultatCodePromoDTO validerEtCalculer(AppliquerCodePromoDTO dto, Long userId) {
        CodePromo promo = codePromoRepository.findValidCode(dto.getCode().toUpperCase(), LocalDateTime.now())
                .orElse(null);

        if (promo == null) {
            return ResultatCodePromoDTO.builder()
                    .valide(false)
                    .message("Code promo invalide ou expiré")
                    .montantOriginal(dto.getMontantCommande())
                    .montantFinal(dto.getMontantCommande())
                    .build();
        }

        if (promo.getMontantMinCommande() != null &&
                dto.getMontantCommande().compareTo(promo.getMontantMinCommande()) < 0) {
            return ResultatCodePromoDTO.builder()
                    .valide(false)
                    .message("Montant minimum de commande requis: " + promo.getMontantMinCommande() + " MAD")
                    .montantOriginal(dto.getMontantCommande())
                    .montantFinal(dto.getMontantCommande())
                    .build();
        }

        BigDecimal reduction = calculerReduction(promo, dto.getMontantCommande());
        BigDecimal montantFinal = dto.getMontantCommande().subtract(reduction).max(BigDecimal.ZERO);

        return ResultatCodePromoDTO.builder()
                .valide(true)
                .message("Code promo appliqué avec succès")
                .codePromo(promo.getCode())
                .montantOriginal(dto.getMontantCommande())
                .montantReduit(reduction)
                .montantFinal(montantFinal)
                .build();
    }

    private BigDecimal calculerReduction(CodePromo promo, BigDecimal montant) {
        if (promo.getTypeReduction() == TypeReduction.POURCENTAGE) {
            BigDecimal reduction = montant.multiply(promo.getValeur()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (promo.getMontantMaxReduction() != null) {
                reduction = reduction.min(promo.getMontantMaxReduction());
            }
            return reduction;
        } else {
            return promo.getValeur().min(montant);
        }
    }

    private CodePromo findById(Long id) {
        return codePromoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Code promo introuvable: " + id));
    }

    private CodePromoDTO toDTO(CodePromo promo) {
        CodePromoDTO dto = new CodePromoDTO();
        dto.setId(promo.getId());
        dto.setCode(promo.getCode());
        dto.setDescription(promo.getDescription());
        dto.setTypeReduction(promo.getTypeReduction());
        dto.setValeur(promo.getValeur());
        dto.setMontantMinCommande(promo.getMontantMinCommande());
        dto.setMontantMaxReduction(promo.getMontantMaxReduction());
        dto.setDateDebut(promo.getDateDebut());
        dto.setDateFin(promo.getDateFin());
        dto.setUsageMax(promo.getUsageMax());
        dto.setUsageCount(promo.getUsageCount());
        dto.setIsActive(promo.getIsActive());
        dto.setCreatedAt(promo.getCreatedAt());
        return dto;
    }
}
