package ma.mysuguclientapp.legacy.seller;

import ma.mysuguclientapp.dtos.OptionGroupDTO;
import ma.mysuguclientapp.dtos.OptionItemDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.enumerations.CategoriePlat;
import ma.mysuguclientapp.legacy.seller.mapper.ProductSellerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProductSellerMapper: pure PlatDTO -> 6valley product JSON translation
 * (no Spring context needed). See docs/superpowers/plans/2026-07-10-vendor-3c-products.md 3c.1.
 */
class ProductSellerMapperTest {

    private final ProductSellerMapper mapper = new ProductSellerMapper();

    private PlatDTO buildPlat() {
        PlatDTO dto = new PlatDTO();
        dto.setId(42L);
        dto.setNom("Tajine Poulet");
        dto.setDescription("Tajine au poulet et olives");
        dto.setPrix(new BigDecimal("55.00"));
        dto.setImageUrl("http://files.mysugu/plats/42.jpg");
        dto.setIsAvailable(true);
        dto.setCategoriePlat(CategoriePlat.PLAT_PRINCIPAL.name());

        OptionItemDTO item = new OptionItemDTO();
        item.setId(1L);
        item.setNom("Frites");
        item.setPrixSupplement(new BigDecimal("10.00"));
        item.setDisponible(true);
        item.setOrdre(0);

        OptionGroupDTO group = new OptionGroupDTO();
        group.setId(1L);
        group.setNom("Accompagnement");
        group.setSelectionMode("SINGLE");
        group.setObligatoire(false);
        group.setOrdre(0);
        group.setItems(List.of(item));

        dto.setOptionGroups(List.of(group));
        return dto;
    }

    @Test
    void toSixValley_maps_plat_fields_to_6valley_product_json() {
        Map<String, Object> json = mapper.toSixValley(buildPlat());

        assertThat(json.get("name")).isEqualTo("Tajine Poulet");
        assertThat(json.get("details")).isEqualTo("Tajine au poulet et olives");
        assertThat(json.get("price")).isEqualTo(new BigDecimal("55.00"));
        assertThat(json.get("status")).isEqualTo(1);
        assertThat(json.get("images")).isEqualTo(List.of("http://files.mysugu/plats/42.jpg"));
        assertThat((List<?>) json.get("choice_options")).isNotEmpty();
    }

    @Test
    void toSixValley_unavailable_plat_has_status_zero_and_no_images() {
        PlatDTO dto = buildPlat();
        dto.setIsAvailable(false);
        dto.setImageUrl(null);

        Map<String, Object> json = mapper.toSixValley(dto);

        assertThat(json.get("status")).isEqualTo(0);
        assertThat(json.get("images")).isEqualTo(List.of());
    }

    @Test
    void listEnvelope_wraps_page_with_6valley_pagination() {
        Page<PlatDTO> page = new PageImpl<>(List.of(buildPlat()), PageRequest.of(0, 10), 1);

        Map<String, Object> envelope = mapper.listEnvelope("products", page);

        assertThat(envelope.get("total_size")).isEqualTo(1);
        assertThat(envelope).containsKeys("limit", "offset");
        assertThat((List<?>) envelope.get("products")).hasSize(1);
    }

    @Test
    void category_maps_enum_to_6valley_category_shape() {
        Map<String, Object> cat = mapper.category(CategoriePlat.ENTREE);

        assertThat(cat.get("name")).isNotNull();
        assertThat(cat.get("slug")).isNotNull();
        assertThat(cat).containsKeys("id", "position", "parent_id", "childes");
    }

    @Test
    void success_returns_message_envelope() {
        Map<String, Object> ack = mapper.success("Produit supprimé.");
        assertThat(ack).containsEntry("message", "Produit supprimé.");
    }

    @Test
    void emptyEnvelope_returns_benign_empty_list() {
        Map<String, Object> env = mapper.emptyEnvelope("products", 10, 0);
        assertThat(env.get("total_size")).isEqualTo(0);
        assertThat((List<?>) env.get("products")).isEmpty();
    }
}
