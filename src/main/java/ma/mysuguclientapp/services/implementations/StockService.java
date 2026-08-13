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
 * Seul point du code qui DÉCRÉMENTE {@code Plat.quantiteStock} (la création/mise à jour du
 * plat, elle, écrit ce champ sans verrou dans {@code PlatServiceImpl} — c'est le commerçant qui
 * fixe son stock, il n'y a pas de concurrence à protéger à ce niveau-là ; seul le décrément
 * concurrent à la commande a besoin d'un verrou, d'où l'existence de cette classe séparée).
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
        // Lecture non verrouillante d'abord : un plat de restaurant (quantiteStock == null,
        // 100% du trafic existant) ne doit JAMAIS prendre de verrou de ligne. La transaction de
        // commande tient ses verrous jusqu'au commit et contient des appels sortants après la
        // boucle de réservation (Stripe, notifications, TikTak) — verrouiller inconditionnellement
        // sérialiserait tout le trafic sur un plat populaire, y compris ces appels réseau, pour
        // aucun bénéfice fonctionnel puisqu'il n'y a rien à protéger quand le stock n'est pas géré.
        Plat platNonVerrouille = platRepository.findById(platId)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouve: " + platId));
        if (platNonVerrouille.getQuantiteStock() == null) {
            return; // stock non géré : aucun verrou necessaire
        }

        Plat plat = platRepository.findByIdForUpdate(platId)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouve: " + platId));

        // Piège Hibernate : ce plat est déjà managé dans le contexte de persistance (par la
        // lecture ci-dessus, ou par une lecture antérieure faite par l'appelant). Le
        // SELECT ... FOR UPDATE acquiert bien le verrou en base mais Hibernate renvoie l'instance
        // déjà managée SANS réhydrater ses champs avec la ligne fraîchement lue. Sans ce
        // refresh(), deux transactions concurrentes peuvent chacune repartir de leur propre
        // lecture non verrouillée (obsolète) et s'écraser l'une l'autre : la seconde à committer
        // gagne, l'autre décrément est perdu — survente silencieuse.
        entityManager.refresh(plat);

        Integer stockActuel = plat.getQuantiteStock();
        if (stockActuel == null) {
            return; // redevenu non géré entre les deux lectures (rarissime) : rien à décrémenter
        }
        if (stockActuel < quantite) {
            throw new BadRequestException("Stock insuffisant pour " + plat.getNom()
                    + " : il reste " + stockActuel + " unité(s)");
        }
        plat.setQuantiteStock(stockActuel - quantite);
        platRepository.save(plat);
    }
}
