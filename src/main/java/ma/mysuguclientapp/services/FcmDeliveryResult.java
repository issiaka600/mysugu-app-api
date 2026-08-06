package ma.mysuguclientapp.services;

import java.util.List;

/** Résultat technique d'un envoi FCM, sans le confondre avec une confirmation de réception mobile. */
public record FcmDeliveryResult(
        int tokensAttempted,
        int tokensSent,
        List<String> firebaseMessageIds,
        List<String> errors
) {
}
