package ma.mysuguclientapp.utils;

import ma.mysuguclientapp.entities.OptionGroup;
import java.text.Normalizer;
import java.util.Locale;

/** Règles communes au panier, aux commandes et au catalogue. */
public final class OptionGroupRules {
    private OptionGroupRules() {}

    public static boolean isRequired(OptionGroup group) {
        String name = group.getNom() == null ? "" : Normalizer.normalize(
                group.getNom(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().toLowerCase(Locale.ROOT);
        return Boolean.TRUE.equals(group.getObligatoire())
                || name.equals("accompagnement") || name.equals("accompagnements");
    }

    public static int minSelections(OptionGroup group) {
        int min = group.getMinSelections() != null ? group.getMinSelections() : 0;
        return isRequired(group) ? Math.max(1, min) : min;
    }
}
