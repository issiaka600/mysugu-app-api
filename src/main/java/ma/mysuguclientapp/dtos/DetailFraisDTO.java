package ma.mysuguclientapp.dtos;

import java.math.BigDecimal;

public record DetailFraisDTO(BigDecimal distanceKm, BigDecimal tarifBase,
                             BigDecimal fraisDistance, BigDecimal fraisLivraison) {}
