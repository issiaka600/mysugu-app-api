package ma.mysuguclientapp.enumerations;

/** Étapes qu'une application mobile peut confirmer pour une notification push. */
public enum NotificationAckEvent {
    RECEIVED,
    OPENED,
    DISPLAYED,
    ACCEPTED,
    REJECTED
}
