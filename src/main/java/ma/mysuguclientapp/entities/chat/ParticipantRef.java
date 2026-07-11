package ma.mysuguclientapp.entities.chat;

import ma.mysuguclientapp.enumerations.ParticipantType;
import java.util.Comparator;

public record ParticipantRef(ParticipantType type, Long id) {
    private static final Comparator<ParticipantRef> ORDER =
        Comparator.comparingInt((ParticipantRef p) -> p.type().ordinal())
                  .thenComparing(p -> p.id() == null ? 0L : p.id());

    public static ParticipantRef[] canonical(ParticipantRef a, ParticipantRef b) {
        return ORDER.compare(a, b) <= 0 ? new ParticipantRef[]{a, b} : new ParticipantRef[]{b, a};
    }
}
