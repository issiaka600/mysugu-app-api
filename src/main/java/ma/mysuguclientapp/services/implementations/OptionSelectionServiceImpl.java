package ma.mysuguclientapp.services.implementations;

import ma.mysuguclientapp.entities.OptionGroup;
import ma.mysuguclientapp.entities.OptionItem;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.enumerations.OptionSelectionMode;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.services.interfaces.OptionSelectionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OptionSelectionServiceImpl implements OptionSelectionService {

    @Override
    public Selection resolve(Plat plat, List<Long> optionItemIds) {
        Set<Long> selectedIds = new HashSet<>();
        if (optionItemIds != null) {
            selectedIds.addAll(optionItemIds);
        }

        List<OptionItem> chosen = new ArrayList<>();
        BigDecimal supplementTotal = BigDecimal.ZERO;
        Set<Long> matched = new HashSet<>();

        List<OptionGroup> groups = plat.getOptionGroups() != null ? plat.getOptionGroups() : List.of();
        for (OptionGroup g : groups) {
            int countInGroup = 0;
            for (OptionItem it : g.getItems()) {
                if (selectedIds.contains(it.getId())) {
                    if (Boolean.FALSE.equals(it.getDisponible())) {
                        throw new BadRequestException("Option indisponible: " + it.getNom());
                    }
                    chosen.add(it);
                    matched.add(it.getId());
                    supplementTotal = supplementTotal.add(
                            it.getPrixSupplement() != null ? it.getPrixSupplement() : BigDecimal.ZERO);
                    countInGroup++;
                }
            }
            valider(g, countInGroup);
        }

        // Tout id sélectionné non rattaché à un groupe du plat => invalide
        for (Long id : selectedIds) {
            if (!matched.contains(id)) {
                throw new BadRequestException("Option invalide pour ce plat: " + id);
            }
        }
        return new Selection(chosen, supplementTotal);
    }

    private void valider(OptionGroup g, int count) {
        int min = g.getMinSelections() != null ? g.getMinSelections() : 0;
        if (Boolean.TRUE.equals(g.getObligatoire()) && min < 1) {
            min = 1;
        }
        if (g.getSelectionMode() == OptionSelectionMode.SINGLE) {
            if (count > 1) {
                throw new BadRequestException("La section \"" + g.getNom() + "\" n'autorise qu'un seul choix");
            }
            if (min >= 1 && count < 1) {
                throw new BadRequestException("La section \"" + g.getNom() + "\" est obligatoire");
            }
            return;
        }
        // MULTIPLE
        if (count < min) {
            throw new BadRequestException("La section \"" + g.getNom() + "\" requiert au moins " + min + " choix");
        }
        if (g.getMaxSelections() != null && count > g.getMaxSelections()) {
            throw new BadRequestException("La section \"" + g.getNom() + "\" autorise au plus " + g.getMaxSelections() + " choix");
        }
    }
}
