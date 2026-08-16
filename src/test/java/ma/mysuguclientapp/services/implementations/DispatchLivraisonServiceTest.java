package ma.mysuguclientapp.services.implementations;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutOffreLivraison;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.OffreLivraisonRepository;
import ma.mysuguclientapp.repositories.TentativeOffreLivraisonRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.FcmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DispatchLivraisonServiceTest {

    private final CommandeRepository commandeRepository = mock(CommandeRepository.class);
    private final OffreLivraisonRepository offreRepository = mock(OffreLivraisonRepository.class);
    private final DispatchLivraisonService service = new DispatchLivraisonService(
            commandeRepository,
            offreRepository,
            mock(TentativeOffreLivraisonRepository.class),
            mock(UserRepository.class),
            mock(FcmService.class),
            mock(ApplicationEventPublisher.class));

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

    private Commande commande(StatutCommande statut) {
        Commande commande = new Commande();
        commande.setStatut(statut);
        commande.setModeReception(ModeReceptionCommande.LIVRAISON);
        return commande;
    }
}
