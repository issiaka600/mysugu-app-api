package ma.mysuguclientapp.legacy;

import ma.mysuguclientapp.legacy.deliveryman.controller.DeliveryManAuthController;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Régression du bug de login livreur : l'app Tiktak envoie {@code country_code} ("212", sans '+')
 * et {@code phone} (le numéro local tel que saisi) séparément, alors que {@code User.telephone}
 * est stocké au format E.164 ("+212..."). Le matching exact précédent ignorait {@code country_code}
 * et échouait (401). La normalisation doit reconstruire la variante E.164 stockée.
 */
class DeliveryManPhoneMatchingTest {

    @Test
    void splitCountryCodeAndLocalNumber_yieldsStoredE164() {
        // Payload réel de l'app : country_code="212", phone="600778899"
        Set<String> c = DeliveryManAuthController.phoneCandidates("212", "600778899");
        assertThat(c).contains("+212600778899");
    }

    @Test
    void localNumberWithTrunkZero_yieldsStoredE164() {
        // L'utilisateur tape parfois le 0 de préfixe national
        assertThat(DeliveryManAuthController.phoneCandidates("212", "0600778899"))
                .contains("+212600778899");
    }

    @Test
    void countryCodeWithPlus_isNormalised() {
        assertThat(DeliveryManAuthController.phoneCandidates("+212", "600778899"))
                .contains("+212600778899");
    }

    @Test
    void alreadyFullInternationalNumber_stillMatches() {
        // Compat : l'app (ou un test) peut envoyer le numéro complet dans phone
        assertThat(DeliveryManAuthController.phoneCandidates("212", "+212600778899"))
                .contains("+212600778899");
    }

    @Test
    void nullOrBlankPhone_yieldsNoCandidates() {
        assertThat(DeliveryManAuthController.phoneCandidates("212", null)).isEmpty();
        assertThat(DeliveryManAuthController.phoneCandidates("212", "   ")).isEmpty();
    }
}
