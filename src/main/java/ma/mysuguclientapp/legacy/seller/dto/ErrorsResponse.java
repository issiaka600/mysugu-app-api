package ma.mysuguclientapp.legacy.seller.dto;

import java.util.List;

/**
 * Forme d'erreur 6valley que l'app vendeur parse : {"errors": [{"code": ..., "message": ...}]}.
 * L'app affiche errors[0].message.
 */
public record ErrorsResponse(List<ErrorItem> errors) {

    public record ErrorItem(String code, String message) {
    }

    public static ErrorsResponse of(String code, String message) {
        return new ErrorsResponse(List.of(new ErrorItem(code, message)));
    }
}
