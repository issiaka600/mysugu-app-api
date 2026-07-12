package ma.mysuguclientapp.chat;

import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticipantRefTest {
    @Test
    void canonical_orders_by_type_then_id_regardless_of_argument_order() {
        var customer = new ParticipantRef(ParticipantType.CUSTOMER, 7L);
        var restaurant = new ParticipantRef(ParticipantType.RESTAURANT, 3L);
        var ab = ParticipantRef.canonical(customer, restaurant);
        var ba = ParticipantRef.canonical(restaurant, customer);
        assertEquals(customer, ab[0]); // CUSTOMER.ordinal() < RESTAURANT.ordinal()
        assertEquals(restaurant, ab[1]);
        assertArrayEquals(ab, ba);     // order-independent
    }

    @Test
    void canonical_breaks_ties_by_id() {
        var a = new ParticipantRef(ParticipantType.CUSTOMER, 9L);
        var b = new ParticipantRef(ParticipantType.CUSTOMER, 4L);
        var out = ParticipantRef.canonical(a, b);
        assertEquals(4L, out[0].id());
        assertEquals(9L, out[1].id());
    }
}
