package ma.mysuguclientapp;

import com.fasterxml.jackson.databind.*;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.*;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.chat.ConversationService;
import ma.mysuguclientapp.services.implementations.*;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import ma.mysuguclientapp.services.location.GoogleMapsLocationService;
import ma.mysuguclientapp.exceptions.LocationContractException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "dispatch.auto.enabled=false")
class RestaurantLocationContractTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RestaurantRepository restaurants;
    @Autowired CommandeRepository orders;
    @Autowired PlatRepository plats;
    @Autowired AdresseLivraisonRepository addresses;
    @Autowired ZoneDeploiementRepository zones;
    @Autowired CodePromoRepository coupons;
    @Autowired PromotionRepository promotions;
    @MockitoBean GoogleMapsLocationService maps;
    @Autowired ConversationService chat;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean MinioService minio;
    @MockitoBean FcmService fcm;
    @MockitoBean EmailService email;
    @MockitoBean ma.mysuguclientapp.services.implementations.NotificationServiceImpl notifications;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    Long restaurantId, orderId;
    String adminToken, driverToken, otherDriverToken, ownerToken, clientToken;
    Long clientId;
    static final double OLD_LAT = 31.6540716, OLD_LNG = -8.0095317;
    // Valeurs de test, sans prétendre localiser l'entrée du restaurant réel.
    static final double NEW_LAT = 31.60, NEW_LNG = -8.02;

    @BeforeEach
    void seed() {
        when(maps.geocode(anyString())).thenThrow(LocationContractException.required());
        when(maps.routeKm(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(inv ->
                BigDecimal.valueOf(5 + Math.abs((double) inv.getArgument(0) - OLD_LAT) * 10
                        + Math.abs((double) inv.getArgument(2) - 14.716677) * 10));
        User admin = user(UserRole.ADMIN);
        User driver = user(UserRole.LIVREUR);
        User other = user(UserRole.LIVREUR);
        User owner = user(UserRole.RESTAURANT_OWNER);
        User client = user(UserRole.CLIENT);
        clientId = client.getId();
        clientToken = jwt.generateToken(client);
        adminToken = jwt.generateToken(admin);
        ownerToken = jwt.generateToken(owner);
        driverToken = jwt.generateToken(driver);
        otherDriverToken = jwt.generateToken(other);
        Restaurant r = new Restaurant();
        r.setNom("GPS test " + System.nanoTime());
        r.setOwner(owner);
        r.setIsActive(true);
        r.setLocalisation(new Localisation(OLD_LAT, OLD_LNG, "Ancienne adresse",
                "Marrakech", "40090", "Maroc"));
        restaurants.save(r);
        restaurantId = r.getId();
        Commande c = new Commande();
        c.setNumeroCommande("GPS-" + System.nanoTime());
        c.setRestaurant(r); c.setClient(client); c.setLivreur(driver);
        c.setStatut(StatutCommande.ASSIGNEE_LIVREUR);
        c.setStatutPaiement(StatutPaiement.EN_ATTENTE);
        c.setModeReception(ModeReceptionCommande.LIVRAISON);
        c.setMontantTotal(BigDecimal.TEN); c.setMontantFinal(BigDecimal.TEN);
        c.setFraisLivraison(BigDecimal.ZERO);
        orderId = orders.save(c).getId();
        chat.append(new ParticipantRef(ParticipantType.RESTAURANT, restaurantId),
                new ParticipantRef(ParticipantType.LIVREUR, driver.getId()),
                "Point de retrait", List.of());
    }

    @Test
    void nestedBackOfficeCoordinatesPersistAndRefreshBothDriverResponses() throws Exception {
        assertDriverPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        var response = update(Map.of(
                "localisation.adresse", "Nouvelle entrée",
                "localisation.latitude", String.valueOf(NEW_LAT),
                "localisation.longitude", String.valueOf(NEW_LNG),
                "locationConfirmed", "true"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertSavedPoint(NEW_LAT, NEW_LNG, "Nouvelle entrée");
        // Même commande et conversation, créées avant la modification.
        assertDriverPoint(NEW_LAT, NEW_LNG, "Nouvelle entrée");
    }

    @Test
    void flatBackOfficeCoordinatesPersist() throws Exception {
        var response = update(Map.of("adresse", "Entrée test",
                "latitude", String.valueOf(NEW_LAT), "longitude", String.valueOf(NEW_LNG),
                "locationConfirmed", "true"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertSavedPoint(NEW_LAT, NEW_LNG, "Entrée test");
        assertDriverPoint(NEW_LAT, NEW_LNG, "Entrée test");
    }

    @Test
    void addressOnlyChangeRejectedWithoutSavingOldGpsUnderNewAddress() throws Exception {
        var response = update(Map.of("localisation.adresse", "Adresse déplacée"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("point GPS");
        assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        assertDriverPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
    }

    @Test
    void partialOrInvalidCoordinatesRejected() throws Exception {
        for (Map<String, String> fields : List.of(
                Map.of("latitude", "31.5"),
                Map.of("longitude", "-8.1"),
                Map.of("latitude", "91", "longitude", "0"),
                Map.of("latitude", "0", "longitude", "-181"),
                Map.of("latitude", "NaN", "longitude", "0"))) {
            var response = update(fields);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
            assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        }
    }

    @Test
    void unrelatedUpdateKeepsCoordinatesAndUnassignedDriverCannotReadLocation() throws Exception {
        var response = update(Map.of("description", "Nouvelle description"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        response = get("/api/v2/delivery-man/seller-location?order_id=" + orderId, otherDriverToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    }

    private void assertDriverPoint(double lat, double lng, String address) throws Exception {
        var response = get("/api/v2/delivery-man/seller-location?order_id=" + orderId, driverToken);
        boolean confirmed = restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt() != null;
        assertThat(response.statusCode()).as(response.body()).isEqualTo(confirmed ? 200 : 409);
        JsonNode gps = json.readTree(response.body());
        if (confirmed) {
            assertThat(gps.get("location_confirmed").asBoolean()).isTrue();
            assertThat(Double.parseDouble(gps.get("latitude").asText())).isEqualTo(lat);
            assertThat(Double.parseDouble(gps.get("longitude").asText())).isEqualTo(lng);
        } else {
            assertThat(gps.get("code").asText()).isEqualTo("RESTAURANT_LOCATION_REQUIRED");
        }
        response = get("/api/v2/delivery-man/messages/list/seller", driverToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode info = json.readTree(response.body()).get("chat").get(0).get("seller_info");
        JsonNode shop = info.get("shop");
        assertThat(shop.get("location_confirmed").asBoolean()).isEqualTo(confirmed);
        assertThat(shop.get("id").asLong()).isEqualTo(restaurantId);
        assertThat(shop.get("latitude").asDouble()).isEqualTo(lat);
        assertThat(shop.get("longitude").asDouble()).isEqualTo(lng);
        assertThat(shop.get("address").asText()).isEqualTo(address);
        assertThat(info.get("shops").get(0)).isEqualTo(shop);
        response = get("/api/v2/delivery-man/current-orders", driverToken);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode orderShop = json.readTree(response.body()).get(0).at("/seller/shop");
        assertThat(orderShop.get("latitude").asDouble()).isEqualTo(lat);
        assertThat(orderShop.get("longitude").asDouble()).isEqualTo(lng);
        assertThat(orderShop.get("location_confirmed").asBoolean()).isEqualTo(confirmed);
    }

    private void assertSavedPoint(double lat, double lng, String address) throws Exception {
        Localisation loc = restaurants.findById(restaurantId).orElseThrow().getLocalisation();
        assertThat(loc.getLatitude()).isEqualTo(lat);
        assertThat(loc.getLongitude()).isEqualTo(lng);
        assertThat(loc.getAdresse()).isEqualTo(address);
        var response = get("/api/restaurants/" + restaurantId, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode publicLoc = json.readTree(response.body()).get("localisation");
        assertThat(publicLoc.get("latitude").asDouble()).isEqualTo(lat);
        assertThat(publicLoc.get("longitude").asDouble()).isEqualTo(lng);
    }

    private HttpResponse<String> update(Map<String, String> fields) throws Exception {
        return updateRequest(fields, "/api/restaurants/" + restaurantId, adminToken);
    }
    private HttpResponse<String> updateSeller(Map<String, String> fields) throws Exception {
        return updateRequest(fields, "/api/v3/seller/shop-update", ownerToken);
    }
    private HttpResponse<String> updateRequest(Map<String, String> fields, String path, String token) throws Exception {
        Map<String, String> all = new LinkedHashMap<>(fields);
        all.put("nom", "GPS test");
        String boundary = "gps-test-boundary";
        StringBuilder body = new StringBuilder();
        all.forEach((key, value) -> body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(key)
                .append("\"\r\n\r\n").append(value).append("\r\n"));
        body.append("--").append(boundary).append("--\r\n");
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method(path.contains("shop-update") ? "POST" : "PUT", HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> jsonRequest(String method, String path, String token, Object payload) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
    private User user(UserRole role) {
        User u = new User();
        u.setEmail("gps-" + role + "-" + System.nanoTime() + "@mysugu.test");
        u.setNom("GPS"); u.setPrenom("Test"); u.setRole(role);
        u.setPassword("unused"); u.setIsActive(true);
        return users.save(u);
    }

    @Test
    void deliveryQuoteRefusesUnconfirmedRestaurantAndReportsDistanceMethodAndFees() throws Exception {
        Restaurant restaurant = restaurants.findById(restaurantId).orElseThrow();
        var addressPayload = Map.of("libelle", "Devis", "adresse", "Destination test", "ville", "Dakar",
                "latitude", 14.716677, "longitude", -17.466675, "locationConfirmed", true);
        var savedAddress = jsonRequest("POST", "/api/users/adresses", clientToken, addressPayload);
        assertThat(savedAddress.statusCode()).isEqualTo(201);
        long addressId = json.readTree(savedAddress.body()).get("id").asLong();
        String base = "/api/commandes/devis?restaurantId=" + restaurantId + "&adresseLivraisonId=" + addressId;
        var unconfirmed = get(base, clientToken);
        assertThat(unconfirmed.statusCode()).isEqualTo(400);
        restaurant.setLocationConfirmedAt(java.time.LocalDateTime.now());
        restaurants.save(restaurant);
        var confirmed = get(base, clientToken);
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
        JsonNode quote = json.readTree(confirmed.body());
        assertThat(quote.get("distanceMethod").asText()).isEqualTo("GOOGLE_ROUTES");
        assertThat(quote.get("distanceKm").asDouble()).isGreaterThan(0);
        assertThat(quote.get("fraisLivraison").decimalValue()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(quote.get("detailFrais").get("distanceKm")).isEqualTo(quote.get("distanceKm"));
        assertThat(quote.get("detailFrais").get("fraisLivraison")).isEqualTo(quote.get("fraisLivraison"));

        var secondAddressPayload = Map.of("libelle", "Bureau", "adresse", "Deuxième destination",
                "ville", "Dakar", "latitude", 14.80, "longitude", -17.50, "locationConfirmed", true);
        var secondAddressResponse = jsonRequest("POST", "/api/users/adresses", clientToken, secondAddressPayload);
        assertThat(secondAddressResponse.statusCode()).isEqualTo(201);
        long secondAddressId = json.readTree(secondAddressResponse.body()).get("id").asLong();
        Restaurant secondRestaurant = new Restaurant();
        secondRestaurant.setNom("GPS test second " + System.nanoTime());
        secondRestaurant.setIsActive(true);
        secondRestaurant.setLocalisation(new Localisation(31.61, -8.03, "Deuxième restaurant", "Marrakech", "", "Maroc"));
        secondRestaurant.setLocationConfirmedAt(java.time.LocalDateTime.now());
        secondRestaurant = restaurants.save(secondRestaurant);
        var secondRestaurantQuote = get("/api/commandes/devis?restaurantId=" + secondRestaurant.getId()
                + "&adresseLivraisonId=" + addressId, clientToken);
        var secondAddressQuote = get("/api/commandes/devis?restaurantId=" + restaurantId
                + "&adresseLivraisonId=" + secondAddressId, clientToken);
        assertThat(secondRestaurantQuote.statusCode()).as(secondRestaurantQuote.body()).isEqualTo(200);
        assertThat(secondAddressQuote.statusCode()).as(secondAddressQuote.body()).isEqualTo(200);
        assertThat(json.readTree(secondRestaurantQuote.body()).get("distanceKm").asDouble())
                .isNotEqualTo(quote.get("distanceKm").asDouble());
        assertThat(json.readTree(secondAddressQuote.body()).get("distanceKm").asDouble())
                .isNotEqualTo(quote.get("distanceKm").asDouble());
    }

    @Test
    void customerAddressRequiresConfirmationAndStoresAddressWithItsPoint() throws Exception {
        var initial = new HashMap<String, Object>();
        initial.put("libelle", "Maison"); initial.put("adresse", "Rue test 1"); initial.put("ville", "Dakar");
        initial.put("latitude", 14.716677); initial.put("longitude", -17.466675);
        var rejected = jsonRequest("POST", "/api/users/adresses", clientToken, initial);
        assertThat(rejected.statusCode()).isEqualTo(400);
        initial.put("locationConfirmed", true);
        var created = jsonRequest("POST", "/api/users/adresses", clientToken, initial);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        JsonNode saved = json.readTree(created.body());
        assertThat(saved.get("locationConfirmed").asBoolean()).isTrue();
        assertThat(saved.get("latitude").asDouble()).isEqualTo(14.716677);
        long addressId = saved.get("id").asLong();
        initial.put("adresse", "Nouvelle rue 2"); initial.put("latitude", 14.72);
        initial.put("locationConfirmed", false);
        var changedWithoutConfirmation = jsonRequest("PUT", "/api/users/adresses/" + addressId, clientToken, initial);
        assertThat(changedWithoutConfirmation.statusCode()).isEqualTo(400);
        initial.put("locationConfirmed", true);
        var changed = jsonRequest("PUT", "/api/users/adresses/" + addressId, clientToken, initial);
        assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);
        JsonNode updated = json.readTree(changed.body());
        assertThat(updated.get("adresse").asText()).isEqualTo("Nouvelle rue 2");
        assertThat(updated.get("latitude").asDouble()).isEqualTo(14.72);
        assertThat(updated.get("locationConfirmed").asBoolean()).isTrue();
    }

    @Test
    void adminChangedRestaurantLocationAlsoRequiresConfirmation() throws Exception {
        var fields = Map.of("localisation.adresse", "Entrée non confirmée",
                "localisation.latitude", String.valueOf(NEW_LAT),
                "localisation.longitude", String.valueOf(NEW_LNG));
        assertThat(update(fields).statusCode()).isEqualTo(400);
        assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        var confirmed = new HashMap<>(fields); confirmed.put("locationConfirmed", "true");
        assertThat(update(confirmed).statusCode()).isEqualTo(200);
        assertSavedPoint(NEW_LAT, NEW_LNG, "Entrée non confirmée");
    }

    @Test
    void sellerChangedLocationRequiresExplicitConfirmation() throws Exception {
        var fields = Map.of("address", "Entrée confirmée", "latitude", "31.6", "longitude", "-8.02");
        var response = updateSeller(fields);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        var confirmed = new HashMap<>(fields);
        confirmed.put("location_confirmed", "true");
        response = updateSeller(confirmed);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("location_confirmed").asBoolean()).isTrue();
        assertThat(restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt()).isNotNull();
        assertSavedPoint(NEW_LAT, NEW_LNG, "Entrée confirmée");
        assertDriverPoint(NEW_LAT, NEW_LNG, "Entrée confirmée");
    }

    @Test
    void sellerCannotReuseOldCoordinatesImplicitly() throws Exception {
        for (var fields : List.of(Map.of("address", "Autre adresse"),
                Map.of("latitude", "31.6", "location_confirmed", "true"),
                Map.of("location_confirmed", "true"))) {
            var response = updateSeller(fields);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
            assertSavedPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
        }
    }

    @Test
    void sellerAddressOrPointChangeNeedsFreshConfirmation() throws Exception {
        var fields = new HashMap<>(Map.of("address", "Ancienne adresse", "latitude", String.valueOf(OLD_LAT),
                "longitude", String.valueOf(OLD_LNG), "location_confirmed", "true"));
        assertThat(updateSeller(fields).statusCode()).isEqualTo(200);
        var first = restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt();
        fields.put("location_confirmed", "false");
        fields.put("address", "Nouvelle entrée");
        assertThat(updateSeller(fields).statusCode()).isEqualTo(422);
        fields.put("address", "Ancienne adresse");
        fields.put("latitude", "31.6");
        assertThat(updateSeller(fields).statusCode()).isEqualTo(422);
        assertThat(restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt()).isEqualTo(first);
        fields.put("location_confirmed", "true");
        assertThat(updateSeller(fields).statusCode()).isEqualTo(200);
        assertThat(restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt()).isAfter(first);
        assertDriverPoint(NEW_LAT, OLD_LNG, "Ancienne adresse");
    }

    @Test
    void unrelatedSellerEditKeepsConfirmation() throws Exception {
        assertThat(updateSeller(Map.of("address", "Ancienne adresse", "latitude", String.valueOf(OLD_LAT),
                "longitude", String.valueOf(OLD_LNG), "location_confirmed", "true")).statusCode()).isEqualTo(200);
        var confirmedAt = restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt();
        assertThat(updateSeller(Map.of("name", "Nom modifié")).statusCode()).isEqualTo(200);
        assertThat(restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt()).isEqualTo(confirmedAt);
        assertDriverPoint(OLD_LAT, OLD_LNG, "Ancienne adresse");
    }

    @Test
    void sellerInvalidCoordinatesRejectedAndMissingOrderDoesNotReturnZeroPoint() throws Exception {
        for (String lat : List.of("NaN", "91")) {
            var response = updateSeller(Map.of("address", "Nouvelle entrée", "latitude", lat,
                    "longitude", "-8.02", "location_confirmed", "true"));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        }
        assertThat(get("/api/v2/delivery-man/seller-location", driverToken).statusCode()).isEqualTo(400);
        var restaurant = restaurants.findById(restaurantId).orElseThrow();
        restaurant.setLocalisation(null);
        restaurants.save(restaurant);
        assertThat(get("/api/v2/delivery-man/seller-location?order_id=" + orderId, driverToken)
                .statusCode()).isEqualTo(409);
    }

    @Test
    void addressOnlyGeocodesAndUpdatesAllDriverAndShopResponses() throws Exception {
        doReturn(new GoogleMapsLocationService.Point(NEW_LAT, NEW_LNG)).when(maps).geocode("12 Rue précise");
        var response = updateSeller(Map.of("address", "12 Rue précise", "name", "Nouveau nom"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var shop = json.readTree(response.body());
        assertThat(shop.get("location_confirmed").asBoolean()).isTrue();
        assertThat(shop.get("latitude").asDouble()).isEqualTo(NEW_LAT);
        assertSavedPoint(NEW_LAT, NEW_LNG, "12 Rue précise");
        assertDriverPoint(NEW_LAT, NEW_LNG, "12 Rue précise");
        var info = json.readTree(get("/api/v3/seller/shop-info", ownerToken).body());
        assertThat(info.get("longitude").asDouble()).isEqualTo(NEW_LNG);
        assertThat(info.get("location_confirmed").asBoolean()).isTrue();
        verify(maps).geocode("12 Rue précise");
    }

    @Test
    void ambiguousAddressAndProviderFailurePreserveEntireShopAndConfirmation() throws Exception {
        var r = restaurants.findById(restaurantId).orElseThrow();
        r.setLocationConfirmedAt(java.time.LocalDateTime.now());
        r.setHorairesOuverture("Horaires conservés"); r.setDescription("Description conservée");
        restaurants.save(r);
        String before = get("/api/v3/seller/shop-info", ownerToken).body();
        var oldTime = restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt();
        var response = updateSeller(Map.of("address", "Ambiguë", "name", "Ne pas enregistrer", "delivery_time", "99"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("RESTAURANT_LOCATION_REQUIRED");
        assertThat(get("/api/v3/seller/shop-info", ownerToken).body()).isEqualTo(before);
        doThrow(LocationContractException.unavailable(false)).when(maps).geocode("Panne");
        response = updateSeller(Map.of("address", "Panne", "name", "Ne pas enregistrer"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(503);
        assertThat(json.readTree(response.body()).get("retryable").asBoolean()).isTrue();
        assertThat(get("/api/v3/seller/shop-info", ownerToken).body()).isEqualTo(before);
        assertThat(restaurants.findById(restaurantId).orElseThrow().getLocationConfirmedAt()).isEqualTo(oldTime);
        assertThat(restaurants.findById(restaurantId).orElseThrow().getHorairesOuverture()).isEqualTo("Horaires conservés");
    }

    @Test
    void newTextAlwaysGeocodesFreshAndUnrelatedSettingsRetainZone() throws Exception {
        var z = new ZoneDeploiement(); z.setNom("Tarif " + System.nanoTime()); z.setRayonKm(new BigDecimal("100"));
        z.setCentreLatitude(OLD_LAT); z.setCentreLongitude(OLD_LNG); z.setIsActive(true);
        z.setPrixParKm(new BigDecimal("4")); z.setFraisLivraisonMin(new BigDecimal("15"));
        z = zones.save(z);
        var r = restaurants.findById(restaurantId).orElseThrow();
        r.setZoneDeploiement(z); r.setHorairesOuverture("Horaires conservés"); restaurants.save(r);
        doReturn(new GoogleMapsLocationService.Point(NEW_LAT, NEW_LNG)).when(maps).geocode("Adresse 1");
        doReturn(new GoogleMapsLocationService.Point(31.62, -8.04)).when(maps).geocode("Adresse 2");
        assertThat(updateSeller(Map.of("address", "Adresse 1")).statusCode()).isEqualTo(200);
        assertThat(updateSeller(Map.of("address", "Adresse 2")).statusCode()).isEqualTo(200);
        assertSavedPoint(31.62, -8.04, "Adresse 2");
        assertThat(updateSeller(Map.of("name", "Réglage" )).statusCode()).isEqualTo(200);
        r = restaurants.findById(restaurantId).orElseThrow();
        assertThat(r.getZoneDeploiement().getId()).isEqualTo(z.getId());
        assertThat(r.getHorairesOuverture()).isEqualTo("Horaires conservés");
        verify(maps).geocode("Adresse 1"); verify(maps).geocode("Adresse 2");
    }

    private Map<String, Object> basket(boolean withCoupon) {
        var r = restaurants.findById(restaurantId).orElseThrow();
        r.setLocationConfirmedAt(java.time.LocalDateTime.now());
        var promo = new Promotion(); promo.setPourcentage(10);
        promo.setDateDebut(java.time.LocalDateTime.now().minusDays(1));
        promo.setDateFin(java.time.LocalDateTime.now().plusDays(1));
        promo = promotions.save(promo); r.setPromotion(promo); restaurants.save(r);
        var address = new AdresseLivraison(); address.setUser(users.findById(clientId).orElseThrow());
        address.setLibelle("Devis"); address.setAdresse("Client existant");
        address.setLatitude(31.61); address.setLongitude(-8.03);
        address.setLocationConfirmedAt(java.time.LocalDateTime.now()); address = addresses.save(address);
        var plat = new Plat(); plat.setNom("Plat devis"); plat.setRestaurant(r); plat.setPrix(new BigDecimal("40"));
        plat.setQuantiteStock(10);
        var group = new OptionGroup(); group.setPlat(plat); group.setNom("Suppléments");
        var item = new OptionItem(); item.setGroup(group); item.setNom("Fromage"); item.setPrixSupplement(new BigDecimal("5"));
        group.getItems().add(item); plat.getOptionGroups().add(group); plat = plats.save(plat);
        var payload = new HashMap<String, Object>();
        payload.put("restaurantId", restaurantId); payload.put("modeReception", "LIVRAISON");
        payload.put("methodePaiement", "ESPECES");
        payload.put("adresseLivraison", Map.of("id", address.getId(), "latitude", 0, "longitude", 0));
        payload.put("lignes", List.of(Map.of("platId", plat.getId(), "quantite", 2,
                "optionItemIds", List.of(plat.getOptionGroups().get(0).getItems().get(0).getId()))));
        if (withCoupon) {
            var coupon = new CodePromo(); coupon.setCode("GPS" + System.nanoTime());
            coupon.setTypeReduction(TypeReduction.MONTANT_FIXE); coupon.setValeur(new BigDecimal("5"));
            coupon = coupons.save(coupon); payload.put("codePromo", coupon.getCode());
        }
        when(maps.routeKm(OLD_LAT, OLD_LNG, 31.61, -8.03)).thenReturn(new BigDecimal("5.251"));
        return payload;
    }

    @Test
    void basketQuoteUsesSavedCustomerPointOptionsAndPromosWithoutWritesAndOrderRecalculates() throws Exception {
        var payload = basket(true);
        long count = orders.count();
        var response = jsonRequest("POST", "/api/commandes/devis", clientToken, payload);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var quote = json.readTree(response.body());
        assertThat(quote.get("sousTotal").decimalValue()).isEqualByComparingTo("90");
        assertThat(quote.get("fraisLivraison").decimalValue()).isEqualByComparingTo("32");
        assertThat(quote.get("montantTotal").decimalValue()).isEqualByComparingTo("122");
        assertThat(quote.get("montantRemise").decimalValue()).isEqualByComparingTo("14");
        assertThat(quote.get("montantFinal").decimalValue()).isEqualByComparingTo("108");
        assertThat(quote.get("remisePromotion").decimalValue()).isEqualByComparingTo("9");
        assertThat(quote.get("distanceKm").decimalValue()).isEqualByComparingTo("5.251");
        assertThat(quote.get("distanceMethod").asText()).isEqualTo("GOOGLE_ROUTES");
        assertThat(orders.count()).isEqualTo(count);
        var line = (Map<?, ?>) ((List<?>) payload.get("lignes")).get(0);
        assertThat(plats.findById(((Number) line.get("platId")).longValue()).orElseThrow().getQuantiteStock()).isEqualTo(10);
        var coupon = coupons.findByCode((String) payload.get("codePromo")).orElseThrow();
        assertThat(coupon.getUsageCount()).isZero();
        assertThat(promotions.findByRestaurantsId(restaurantId).get(0).getUsageCount()).isZero();
        response = jsonRequest("POST", "/api/commandes", clientToken, payload);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        var order = json.readTree(response.body());
        for (String field : List.of("sousTotal", "fraisLivraison", "montantTotal", "montantRemise", "montantFinal"))
            assertThat(order.get(field).decimalValue()).as(field).isEqualByComparingTo(quote.get(field).decimalValue());
        assertThat(order.at("/restaurant/locationConfirmed").asBoolean()).isTrue();
        assertThat(order.at("/restaurant/localisation/latitude").asDouble()).isEqualTo(OLD_LAT);
        assertThat(coupons.findById(coupon.getId()).orElseThrow().getUsageCount()).isEqualTo(1);
        assertThat(promotions.findByRestaurantsId(restaurantId).get(0).getUsageCount()).isEqualTo(1);
        verify(maps, times(2)).routeKm(OLD_LAT, OLD_LNG, 31.61, -8.03);
    }

    @Test
    void routesFailureRollsBackOrderStockAndPromoUsageAndQuoteHasRetryableError() throws Exception {
        var payload = basket(true);
        var line = (Map<?, ?>) ((List<?>) payload.get("lignes")).get(0);
        long platId = ((Number) line.get("platId")).longValue();
        long count = orders.count();
        when(maps.routeKm(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(LocationContractException.unavailable(true));
        for (String route : List.of("/api/commandes/devis", "/api/commandes")) {
            var response = jsonRequest("POST", route, clientToken, payload);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(503);
            assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("GOOGLE_ROUTES_UNAVAILABLE");
        }
        assertThat(orders.count()).isEqualTo(count);
        assertThat(plats.findById(platId).orElseThrow().getQuantiteStock()).isEqualTo(10);
        assertThat(coupons.findByCode((String) payload.get("codePromo")).orElseThrow().getUsageCount()).isZero();
    }

    @Test
    void quoteAcceptsExistingAddressObjectButRejectsForeignAndUnknownPoints() throws Exception {
        var payload = basket(false);
        long addressId = ((Number) ((Map<?, ?>) payload.get("adresseLivraison")).get("id")).longValue();
        payload.put("adresseLivraison", Map.of("adresse", "Client existant", "latitude", 31.61, "longitude", -8.03));
        assertThat(jsonRequest("POST", "/api/commandes/devis", clientToken, payload).statusCode()).isEqualTo(200);
        payload.put("adresseLivraison", Map.of("adresse", "Point forgé", "latitude", 0, "longitude", 0));
        assertThat(jsonRequest("POST", "/api/commandes/devis", clientToken, payload).statusCode()).isEqualTo(400);
        payload.put("adresseLivraison", Map.of("id", addressId));
        var foreign = user(UserRole.CLIENT); String foreignToken = jwt.generateToken(foreign);
        assertThat(jsonRequest("POST", "/api/commandes/devis", foreignToken, payload).statusCode()).isEqualTo(404);
        payload.put("clientId", foreign.getId());
        assertThat(jsonRequest("POST", "/api/commandes/devis", clientToken, payload).statusCode()).isEqualTo(403);
    }

    @Test
    void pickupQuoteAndOrderRemainAvailableWithoutLocationOrGoogleRoutes() throws Exception {
        var payload = basket(false);
        payload.put("modeReception", "RETRAIT_SUR_PLACE"); payload.remove("adresseLivraison");
        var restaurant = restaurants.findById(restaurantId).orElseThrow();
        restaurant.setLocationConfirmedAt(null); restaurants.save(restaurant);
        clearInvocations(maps);
        for (String route : List.of("/api/commandes/devis", "/api/commandes")) {
            var response = jsonRequest("POST", route, clientToken, payload);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(route.endsWith("devis") ? 200 : 201);
            var result = json.readTree(response.body());
            assertThat(result.get("fraisLivraison").decimalValue()).isEqualByComparingTo("0");
            assertThat(result.get("montantFinal").decimalValue()).isEqualByComparingTo("81");
        }
        verifyNoInteractions(maps);
    }
}
