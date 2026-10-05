package ma.mysuguclientapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.mysuguclientapp.dtos.*;
import ma.mysuguclientapp.dtos.cart.AjouterItemDTO;
import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.*;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.repositories.*;
import ma.mysuguclientapp.services.implementations.PanierServiceImpl;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import ma.mysuguclientapp.services.interfaces.PlatOptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"dispatch.auto.enabled=false", "tiktak.integration.enabled=false"})
class OrderOptionsContractTest {
    @Autowired UserRepository users;
    @Autowired RestaurantRepository restaurants;
    @Autowired PlatRepository plats;
    @Autowired CommandeRepository orders;
    @Autowired PanierRepository carts;
    @Autowired PanierServiceImpl cartService;
    @Autowired CommandeService orderService;
    @Autowired PlatOptionService optionService;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager txManager;
    @LocalServerPort int port;
    TransactionTemplate tx;
    Long clientId, ownerId, restaurantId, platId;
    List<Long> ids;
    String ownerEmail;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            ownerEmail = "options-owner-" + System.nanoTime() + "@mysugu.test";
            User owner = newUser(ownerEmail, UserRole.RESTAURANT_OWNER);
            ownerId = owner.getId();
            clientId = newUser("options-client-" + System.nanoTime() + "@mysugu.test", UserRole.CLIENT).getId();
            Restaurant r = new Restaurant();
            r.setNom("Options " + System.nanoTime());
            r.setOwner(owner);
            r.setIsActive(true);
            r.setVertical(Vertical.ALIMENTAIRE);
            r.setCommissionPourcentage(BigDecimal.ZERO);
            restaurants.save(r);
            restaurantId = r.getId();
            Plat p = new Plat();
            p.setNom("Plat test");
            p.setPrix(new BigDecimal("100.00"));
            p.setRestaurant(r);
            p.setIsAvailable(true);
            p.setQuantiteStock(100);
            // Ancienne configuration : accompagnement facultatif en base.
            addGroup(p, "Accompagnement", "Frites", "0.00");
            addGroup(p, "Suppléments", "Fromage", "10.00");
            addGroup(p, "Boissons", "Jus", "15.00");
            plats.saveAndFlush(p);
            platId = p.getId();
            ids = p.getOptionGroups().stream().map(g -> g.getItems().getFirst().getId()).toList();
        });
    }

    @Test
    void missingAccompanimentRejectedAtCartAndCheckout() {
        assertRejected(List.of());
        assertRejected(ids.subList(1, 3));
        tx.executeWithoutResult(status -> {
            assertThat(carts.findByUserId(clientId)).isEmpty();
            assertThat(plats.findById(platId).orElseThrow().getQuantiteStock()).isEqualTo(100);
        });
        var groups = optionService.getOptions(platId);
        assertThat(groups.getFirst().getObligatoire()).isTrue();
        assertThat(groups.getFirst().getMinSelections()).isEqualTo(1);
    }

    @Test
    void foreignUnavailableAndTooManyOptionsRejected() {
        List<Long> foreign = new ArrayList<>(ids);
        foreign.add(Long.MAX_VALUE);
        assertRejected(foreign);
        tx.executeWithoutResult(status -> plats.findById(platId).orElseThrow()
                .getOptionGroups().getFirst().getItems().getFirst().setDisponible(false));
        assertRejected(ids);
        Long extraId = tx.execute(status -> {
            Plat p = plats.findById(platId).orElseThrow();
            OptionGroup group = p.getOptionGroups().getFirst();
            group.getItems().getFirst().setDisponible(true);
            OptionItem extra = new OptionItem();
            extra.setGroup(group);
            extra.setNom("Riz");
            group.getItems().add(extra);
            plats.saveAndFlush(p);
            return extra.getId();
        });
        List<Long> tooMany = new ArrayList<>(ids);
        tooMany.add(extraId);
        assertRejected(tooMany);
        tx.executeWithoutResult(status -> {
            OptionGroup g = plats.findById(platId).orElseThrow().getOptionGroups().getFirst();
            g.setSelectionMode(OptionSelectionMode.MULTIPLE);
            g.setMinSelections(2);
            g.setMaxSelections(2);
        });
        assertRejected(ids);
        List<Long> validMultiple = new ArrayList<>(ids);
        validMultiple.add(extraId);
        tx.executeWithoutResult(status -> {
            OptionGroup g = plats.findById(platId).orElseThrow().getOptionGroups().getFirst();
            OptionItem extra = new OptionItem();
            extra.setGroup(g);
            extra.setNom("Salade");
            g.getItems().add(extra);
            plats.saveAndFlush(g.getPlat());
            validMultiple.add(extra.getId());
        });
        assertRejected(validMultiple);
    }

    @Test
    void cartCheckoutAndSellerHttpPreserveAllOptionsAndPrices() throws Exception {
        Map<String, Object> cart = cartService.ajouterItem(clientId, cartDto(ids));
        assertThat((BigDecimal) cart.get("montantTotal")).isEqualByComparingTo("250.00");
        CommandeDTO created = orderService.createCommande(orderDto(ids));
        tx.executeWithoutResult(status -> {
            Commande saved = orders.findById(created.getId()).orElseThrow();
            assertThat(saved.getLignesCommande()).singleElement().satisfies(line -> {
                assertThat(line.getPrixUnitaire()).isEqualByComparingTo("125.00");
                assertThat(line.getMontantTotal()).isEqualByComparingTo("250.00");
                assertThat(line.getOptions()).hasSize(3);
                assertThat(line.getOptions()).extracting(LigneCommandeOption::getOptionNom)
                        .containsExactlyInAnyOrder("Frites", "Fromage", "Jus");
            });
            // Les noms/prix historiques restent stables après modification du catalogue.
            Plat p = plats.findById(platId).orElseThrow();
            for (OptionGroup g : p.getOptionGroups()) {
                g.setNom("Groupe modifié");
                g.getItems().getFirst().setNom("Option modifiée");
                g.getItems().getFirst().setPrixSupplement(new BigDecimal("999.00"));
            }
        });
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v3/seller/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("email", ownerEmail, "password", "demo1234")))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        String token = json.readTree(login.body()).get("token").asText();
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v3/seller/orders/" + created.getId()))
                .header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode line = json.readTree(response.body()).get(0);
        assertThat(line.get("price").decimalValue()).isEqualByComparingTo("125.00");
        JsonNode options = line.get("options");
        assertThat(options).hasSize(3);
        Map<String, JsonNode> byName = new HashMap<>();
        options.forEach(o -> byName.put(o.get("optionNom").asText(), o));
        assertOption(byName.get("Frites"), "Accompagnement", "0.00");
        assertOption(byName.get("Fromage"), "Suppléments", "10.00");
        assertOption(byName.get("Jus"), "Boissons", "15.00");
    }

    private void assertOption(JsonNode option, String group, String price) {
        assertThat(option).isNotNull();
        assertThat(option.get("optionGroupNom").asText()).isEqualTo(group);
        assertThat(option.get("prixSupplement").decimalValue()).isEqualByComparingTo(price);
    }
    private void assertRejected(List<Long> choices) {
        assertThatThrownBy(() -> cartService.ajouterItem(clientId, cartDto(choices)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> orderService.createCommande(orderDto(choices)))
                .isInstanceOf(BadRequestException.class);
    }
    private AjouterItemDTO cartDto(List<Long> choices) {
        AjouterItemDTO d = new AjouterItemDTO();
        d.setPlatId(platId); d.setQuantite(2); d.setOptionItemIds(choices);
        return d;
    }
    private CommandeCreateDTO orderDto(List<Long> choices) {
        LigneCommandeCreateDTO l = new LigneCommandeCreateDTO();
        l.setPlatId(platId); l.setQuantite(2); l.setOptionItemIds(choices);
        CommandeCreateDTO d = new CommandeCreateDTO();
        d.setClientId(clientId); d.setRestaurantId(restaurantId);
        d.setModeReception("RETRAIT_SUR_PLACE"); d.setMethodePaiement("ESPECES");
        d.setLignes(List.of(l));
        return d;
    }
    private User newUser(String email, UserRole role) {
        User u = new User();
        u.setEmail(email); u.setPassword(encoder.encode("demo1234"));
        u.setNom("Test"); u.setPrenom("Options"); u.setRole(role); u.setIsActive(true);
        return users.save(u);
    }
    private void addGroup(Plat p, String name, String itemName, String price) {
        OptionGroup g = new OptionGroup();
        g.setPlat(p); g.setNom(name);
        OptionItem i = new OptionItem();
        i.setGroup(g); i.setNom(itemName); i.setPrixSupplement(new BigDecimal(price));
        g.getItems().add(i); p.getOptionGroups().add(g);
    }
}
