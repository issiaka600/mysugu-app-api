package ma.mysuguclientapp.services.implementations;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.PlatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seul point du code qui écrit {@code Plat.quantiteStock}.
 *
 * <p>Un plat dont {@code quantiteStock} est null n'est pas géré en stock : ni verrou,
 * ni décrément, ni blocage. C'est le cas de tous les plats de restaurant.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final PlatRepository platRepository;
    private final EntityManager entityManager;

    /**
     * Réserve {@code quantite} unités du produit, sous verrou pessimiste.
     * À appeler dans la transaction qui écrit la commande.
     *
     * @throws BadRequestException si le stock disponible est insuffisant
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserver(Long platId, int quantite) {
        Plat plat = platRepository.findByIdForUpdate(platId)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouve: " + platId));

        // Piège Hibernate : si ce plat a déjà été chargé plus tôt dans la même transaction
        // (ex. la lecture non verrouillante faite par CommandeServiceImpl avant d'appeler
        // reserver()), le SELECT ... FOR UPDATE ci-dessus acquiert bien le verrou en base mais
        // Hibernate renvoie l'instance déjà managée SANS réhydrater ses champs avec la ligne
        // fraîchement lue. Sans ce refresh(), deux transactions concurrentes peuvent chacune
        // repartir de leur propre lecture non verrouillée (obsolète) et s'écraser l'une
        // l'autre : la seconde à committer gagne, l'autre décrément est perdu.
        entityManager.refresh(plat);

        if (plat.getQuantiteStock() == null) {
            return; // stock non géré
        }
        if (plat.getQuantiteStock() < quantite) {
            throw new BadRequestException("Stock insuffisant pour " + plat.getNom()
                    + " : il reste " + plat.getQuantiteStock() + " unité(s)");
        }
        plat.setQuantiteStock(plat.getQuantiteStock() - quantite);
        platRepository.save(plat);
    }
}
