package ma.mysuguclientapp.services.implementations;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Localisation;
import ma.mysuguclientapp.entities.OffreLivraison;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutOffreLivraison;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.OffreLivraisonRepository;
import ma.mysuguclientapp.repositories.TentativeOffreLivraisonRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.FcmDeliveryResult;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DispatchLivraisonServiceTest {

    private final CommandeRepository commandeRepository = mock(CommandeRepository.class);
    private final OffreLivraisonRepository offreRepository = mock(OffreLivraisonRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FcmService fcmService = mock(FcmService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final DispatchLivraisonService service = new DispatchLivraisonService(
            commandeRepository,
            offreRepository,
            mock(TentativeOffreLivraisonRepository.class),
            userRepository,
            fcmService,
            eventPublisher);

    DispatchLivraisonServiceTest() {
        ReflectionTestUtils.setField(service, "offerDurationSeconds", 30L);
        ReflectionTestUtils.setField(service, "offerAlertIntervalSeconds", 20L);
        ReflectionTestUtils.setField(service, "maxLocationAgeSeconds", 120L);
        ReflectionTestUtils.setField(service, "maxSellerLocationAgeSeconds", 86400L);
    }

    @Test
    void neProposeAucunLivreurAvantLaPreparation() {
        Commande commande = commande(StatutCommande.CONFIRMEE);
        when(commandeRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(commande));

        service.proposerProchainLivreur(42L);

        verifyNoInteractions(offreRepository);
    }

    @ParameterizedTest
    @EnumSource(value = StatutCommande.class, names = {"EN_PREPARATION", "PRETE"})
    void verifieUneOffreExistantePendantEtApresLaPreparation(StatutCommande statut) {
        Commande commande = commande(statut);
        when(commandeRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(commande));
        when(offreRepository.findByCommandeIdAndStatut(42L, StatutOffreLivraison.PROPOSEE))
                .thenReturn(Optional.of(mock(ma.mysuguclientapp.entities.OffreLivraison.class)));

        service.proposerProchainLivreur(42L);

        verify(offreRepository).findByCommandeIdAndStatut(42L, StatutOffreLivraison.PROPOSEE);
    }

    @Test
    void accepterPendantLaPreparationReserveLeLivreurSansMarquerLaCommandePrete() {
        Commande commande = commande(StatutCommande.EN_PREPARATION);
        User livreur = new User();
        livreur.setId(7L);
        livreur.setLivreurDisponible(true);
        OffreLivraison offre = OffreLivraison.builder()
                .commande(commande)
                .livreur(livreur)
                .statut(StatutOffreLivraison.PROPOSEE)
                .expiresAt(LocalDateTime.now().plusSeconds(30))
                .build();
        when(commandeRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(commande));
        when(offreRepository.findForUpdate(42L, 7L, StatutOffreLivraison.PROPOSEE))
                .thenReturn(Optional.of(offre));
        when(commandeRepository.countByLivreurIdAndStatutIn(eq(7L), anyList()))
                .thenReturn(0L);
        when(commandeRepository.save(commande)).thenReturn(commande);

        Commande resultat = service.accepterOffre(42L, livreur);

        assertThat(resultat.getLivreur()).isSameAs(livreur);
        assertThat(resultat.getStatut()).isEqualTo(StatutCommande.EN_PREPARATION);
        assertThat(livreur.getLivreurDisponible()).isFalse();
    }

    @Test
    void choisitLeLivreurLePlusProcheDeLaPositionRecenteDuVendeur() {
        Commande commande = commandeAvecRestaurant(StatutCommande.EN_PREPARATION,
                localisation(14.7000, -17.4500));
        User vendeur = userAvecPosition(99L, 14.7167, -17.4677);
        vendeur.setLastLocationAt(LocalDateTime.now());
        commande.getRestaurant().setOwner(vendeur);

        User procheVendeur = livreur(7L, 14.7170, -17.4680);
        User procheRestaurant = livreur(8L, 14.7001, -17.4501);
        preparerRecherche(commande, procheVendeur, procheRestaurant);

        service.proposerProchainLivreur(42L);

        assertThat(offreSauvegardee.getLivreur()).isSameAs(procheVendeur);
    }

    @Test
    void notificationOffreExposeIdentifiantsTtlEtExpirationAbsolue() {
        Commande commande = commandeAvecRestaurant(StatutCommande.EN_PREPARATION,
                localisation(14.7000, -17.4500));
        User vendeur = userAvecPosition(99L, 14.7000, -17.4500);
        vendeur.setLastLocationAt(LocalDateTime.now());
        commande.getRestaurant().setOwner(vendeur);
        preparerRecherche(commande, livreur(7L, 14.7001, -17.4501));

        service.proposerProchainLivreur(42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(fcmService).sendToUserWithResult(eq(7L), eq("Nouvelle livraison"),
                eq("Une commande est disponible"), data.capture());
        assertThat(data.getValue())
                .containsEntry("type", "order")
                .containsEntry("event", "new_delivery")
                .containsEntry("order_id", "42")
                .containsEntry("delivery_offer_id", "123")
                .containsEntry("ttlSeconds", "30");
        assertThat(data.getValue().get("expires_at")).endsWith("Z");
    }

    @Test
    void utiliseLaPositionDuRestaurantSiLaPositionDuVendeurEstTropAncienne() {
        Commande commande = commandeAvecRestaurant(StatutCommande.EN_PREPARATION,
                localisation(14.7000, -17.4500));
        User vendeur = userAvecPosition(99L, 14.7167, -17.4677);
        vendeur.setLastLocationAt(LocalDateTime.now().minusDays(2));
        commande.getRestaurant().setOwner(vendeur);

        User procheVendeur = livreur(7L, 14.7170, -17.4680);
        User procheRestaurant = livreur(8L, 14.7001, -17.4501);
        preparerRecherche(commande, procheVendeur, procheRestaurant);

        service.proposerProchainLivreur(42L);

        assertThat(offreSauvegardee.getLivreur()).isSameAs(procheRestaurant);
    }

    @Test
    void refusDeclencheLaRechercheDuLivreurSuivantApresCommit() {
        Commande commande = commande(StatutCommande.EN_PREPARATION);
        User livreur = new User();
        livreur.setId(7L);
        OffreLivraison offre = OffreLivraison.builder()
                .commande(commande)
                .livreur(livreur)
                .statut(StatutOffreLivraison.PROPOSEE)
                .expiresAt(LocalDateTime.now().plusSeconds(30))
                .build();
        when(offreRepository.findForUpdate(42L, 7L, StatutOffreLivraison.PROPOSEE))
                .thenReturn(Optional.of(offre));

        service.refuserOffre(42L, livreur);

        assertThat(offre.getStatut()).isEqualTo(StatutOffreLivraison.REFUSEE);
        assertThat(offre.getRespondedAt()).isNotNull();
        verify(eventPublisher).publishEvent(new ma.mysuguclientapp.events.DispatchLivraisonEvent(42L));
    }

    @Test
    void relanceUneCommandeResteeSansOffreQuandUnLivreurDevientDisponible() {
        Commande commande = commandeAvecRestaurant(StatutCommande.EN_PREPARATION,
                localisation(14.7000, -17.4500));
        User livreur = livreur(7L, 14.7001, -17.4501);
        when(commandeRepository.findByStatutInAndLivreurIsNullOrderByCreatedAtAsc(anyList()))
                .thenReturn(List.of(commande));
        preparerRecherche(commande, livreur);

        service.relancerCommandesSansOffre();

        assertThat(offreSauvegardee.getLivreur()).isSameAs(livreur);
        verify(commandeRepository, times(1)).findByIdForUpdate(42L);
    }

    private OffreLivraison offreSauvegardee;

    private void preparerRecherche(Commande commande, User... livreurs) {
        when(commandeRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(commande));
        when(offreRepository.findByCommandeIdAndStatut(42L, StatutOffreLivraison.PROPOSEE))
                .thenReturn(Optional.empty());
        when(offreRepository.findByCommandeIdOrderBySequenceNumberAsc(42L)).thenReturn(List.of());
        when(userRepository.findByRoleAndIsActiveAndLivreurDisponible(any(), eq(true), eq(true)))
                .thenReturn(List.of(livreurs));
        when(fcmService.sendToUserWithResult(any(), any(), any(), any()))
                .thenReturn(new FcmDeliveryResult(0, 0, List.of(), List.of()));
        when(offreRepository.save(any(OffreLivraison.class))).thenAnswer(invocation -> {
            offreSauvegardee = invocation.getArgument(0);
            offreSauvegardee.setId(123L);
            return offreSauvegardee;
        });
    }

    private User livreur(Long id, double latitude, double longitude) {
        User livreur = userAvecPosition(id, latitude, longitude);
        livreur.setIsActive(true);
        livreur.setLivreurDisponible(true);
        livreur.setLastLocationAt(LocalDateTime.now());
        return livreur;
    }

    private User userAvecPosition(Long id, double latitude, double longitude) {
        User user = new User();
        user.setId(id);
        user.setLocalisation(localisation(latitude, longitude));
        return user;
    }

    private Localisation localisation(double latitude, double longitude) {
        Localisation localisation = new Localisation();
        localisation.setLatitude(latitude);
        localisation.setLongitude(longitude);
        return localisation;
    }

    private Commande commandeAvecRestaurant(StatutCommande statut, Localisation localisation) {
        Commande commande = commande(statut);
        Restaurant restaurant = new Restaurant();
        restaurant.setLocalisation(localisation);
        commande.setRestaurant(restaurant);
        return commande;
    }

    private Commande commande(StatutCommande statut) {
        Commande commande = new Commande();
        commande.setId(42L);
        commande.setStatut(statut);
        commande.setModeReception(ModeReceptionCommande.LIVRAISON);
        return commande;
    }
}
