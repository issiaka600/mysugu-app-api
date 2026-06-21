package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.entities.OptionItem;
import ma.mysuguclientapp.entities.Plat;

import java.math.BigDecimal;
import java.util.List;

public interface OptionSelectionService {

    /** Résultat validé d'une sélection d'options. */
    class Selection {
        private final List<OptionItem> items;
        private final BigDecimal supplementTotal;
        public Selection(List<OptionItem> items, BigDecimal supplementTotal) {
            this.items = items; this.supplementTotal = supplementTotal;
        }
        public List<OptionItem> getItems() { return items; }
        public BigDecimal getSupplementTotal() { return supplementTotal; }
    }

    /**
     * Valide les identifiants d'options choisis pour un plat (appartenance, disponibilité,
     * contraintes par groupe : obligatoire/min/max/SINGLE) et calcule le supplément total.
     * @throws ma.mysuguclientapp.exceptions.BadRequestException si la sélection est invalide.
     */
    Selection resolve(Plat plat, List<Long> optionItemIds);
}
