package ma.mysuguclientapp.services.implementations;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.LigneCommande;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.PlatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

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
    private final CommandeRepository commandeRepository;
    private final EntityManager entityManager;

    /**
     * Réserve {@code quantite} unités du produit. Si son stock est géré
     * ({@code quantiteStock != null}), la réservation se fait sous verrou pessimiste. Si son
     * stock n'est pas géré (cas de tous les plats de restaurant), aucun verrou n'est pris et
     * l'appel est un no-op. À appeler dans la transaction qui écrit la commande.
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

    /**
     * Re-crédite le stock des lignes d'une commande annulée. Idempotent : une commande
     * déjà restituée n'est jamais re-créditée une seconde fois — c'est le marqueur
     * {@code Commande.stockRestitue} qui porte cette garantie, pas l'appelant : les quatre
     * chemins d'annulation connus ({@code cancelCommande}, {@code updateCommandeStatus},
     * {@code DeliveryManLifecycleController.updateOrderStatus}, {@code TikTakOrderIntegrationService
     * .applyStatusFromTikTak}) appellent cette méthode sans se coordonner entre eux.
     *
     * <p>Pour une commande persistée, la garde d'idempotence est protégée par un verrou
     * pessimiste sur la ligne {@code commandes} elle-même ({@link CommandeRepository#findByIdForUpdate}),
     * même patron que le verrou sur {@code Plat} dans {@link #reserver}. C'est délibéré : une
     * garde purement en mémoire sur {@code commande.getStockRestitue()} ne suffit pas à empêcher
     * un entrelacement de deux <em>transactions</em> concurrentes (double clic sur « annuler »,
     * annulation client et vendeur simultanées) — les deux peuvent lire {@code false} avant que
     * l'une des deux ne committe : une garde de type lecture-puis-action, sans verrou, est fausse
     * par construction, quel que soit le mécanisme d'écriture utilisé ensuite. Une première
     * tentative avait remplacé le verrou par un simple {@code UPDATE ... WHERE stock_restitue =
     * false} conditionnel ; elle a semblé insuffisante à l'usage (les deux transactions
     * obtenaient chacune une ligne affectée), mais ce n'était PAS un défaut de ce mécanisme en
     * lui-même — sous PostgreSQL en READ COMMITTED, cet UPDATE conditionnel bloque correctement
     * la seconde transaction puis réévalue son prédicat sur la version committée, donnant 0 ligne
     * affectée pour la seconde. La vraie cause, découverte ensuite (voir le paragraphe sur
     * l'ordre d'appel ci-dessous), est que l'appelant modifiait déjà {@code commande} avant cet
     * appel : le flush Hibernate qui suivait réécrivait toute la ligne avec des valeurs obsolètes
     * et défaisait aussi bien l'UPDATE conditionnel que n'importe quel autre mécanisme
     * d'idempotence. Le verrou pessimiste reste la protection retenue ici, nécessaire
     * indépendamment de ce bug d'ordre.
     *
     * <p><b>Appeler CETTE méthode avant toute autre mutation de {@code commande}</b> — avant
     * {@code setStatut}, {@code setRaisonAnnulation}, etc. C'est une contrainte réelle, pas une
     * préférence de style : {@code Commande} n'a pas {@code @DynamicUpdate}, donc le prochain
     * flush Hibernate ré-écrit TOUTES ses colonnes avec les valeurs actuellement en mémoire. Si
     * l'appelant a déjà modifié {@code commande} (ex. {@code setStatut(ANNULEE)}) avant d'appeler
     * {@code restituer()}, ce changement est en attente (non flushé) au moment où
     * {@link CommandeRepository#findByIdForUpdate} s'exécute ; l'auto-flush qui précède cette
     * requête verrouillante écrit alors la ligne entière — {@code stock_restitue} inclus — avec
     * l'état obsolète encore en mémoire (celui d'avant le verrou). Concrètement : si une autre
     * transaction a entre-temps committé {@code stock_restitue = true}, cet auto-flush
     * <em>l'écrase silencieusement à false</em>, et le {@code refresh()} qui suit relit cette
     * valeur tout juste ré-écrite — la garde d'idempotence croit alors, à tort, que rien n'a
     * encore été restitué, et crédite le stock une seconde fois. Bug réel, reproduit et corrigé
     * pendant cette tâche : les quatre appelants respectent maintenant cet ordre.</p>
     *
     * <p>Pour une commande transitoire ({@code id == null}, cas des appels directs en test sur un
     * objet jamais persisté), il n'y a pas de ligne en base à verrouiller : un simple test en
     * mémoire suffit, il n'y a rien de concurrent à protéger.</p>
     *
     * <p><b>Restitution partielle assumée</b> : si une ligne porte sur un produit supprimé
     * entre-temps, elle est ignorée silencieusement (voir plus bas), et le marqueur est quand
     * même posé — il n'y a pas de reprise ultérieure pour recréditer cette ligne-là. C'est
     * volontaire : une commande doit toujours pouvoir être annulée, y compris quand une partie de
     * son stock n'est plus recréditable.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restituer(Commande commande) {
        if (commande.getId() != null) {
            java.util.Optional<Commande> verrouillee = commandeRepository.findByIdForUpdate(commande.getId());
            if (verrouillee.isEmpty()) {
                // Ligne "commandes" introuvable : suppression concurrente (n'arrive pas en usage
                // normal, une commande n'est jamais supprimée par l'application), ou entité
                // détachée reconstruite avec un id qui ne correspond plus à rien en base. On ne
                // peut ni verrouiller ni garantir l'idempotence pour cette ligne ; on n'essaie pas
                // de créditer sans protection, mais on ne fait pas non plus échouer l'appelant
                // pour autant (même philosophie que le produit supprimé, plus bas).
                return;
            }
            commande = verrouillee.get();

            // Même piège Hibernate que pour Plat dans reserver() : le SELECT ... FOR UPDATE
            // acquiert bien le verrou en base mais Hibernate renvoie l'instance déjà managée
            // (chargée plus haut dans la pile d'appel par findCommande()) SANS la réhydrater.
            // Sans un rafraîchissement, la transaction qui obtient le verrou en second ne voit pas
            // que stockRestitue est déjà passé à true par la première, et crédite le stock une
            // seconde fois — exactement le bug que le verrou est censé empêcher.
            //
            // Rafraîchissement CIBLÉ sur ce seul champ, PAS entityManager.refresh(commande) sur
            // l'entité entière : Commande.lignesCommande est cascade=ALL (donc REFRESH inclus), et
            // LigneCommande.options l'est aussi — un refresh() complet cascade dans ces collections
            // et peut entrer en conflit avec une collection déjà chargée ailleurs dans le même
            // contexte de persistance (Hibernate : "Found two representations of same collection"),
            // un vrai crash reproduit pendant cette tâche dès qu'une commande venait d'être créée
            // puis annulée dans la même transaction. On n'a besoin de fraîcheur que sur
            // stockRestitue ; une simple projection scalaire l'obtient sans jamais toucher aux
            // collections. getSingleResult() est sûr ici : on tient le verrou pessimiste sur cette
            // ligne depuis findByIdForUpdate ci-dessus, donc aucune transaction concurrente ne peut
            // l'avoir supprimée entre-temps — contrairement au premier appel, où ce risque existe
            // et où NoResultException doit être traité explicitement (voir plus haut).
            Boolean stockRestitueFrais = entityManager.createQuery(
                            "SELECT c.stockRestitue FROM Commande c WHERE c.id = :id", Boolean.class)
                    .setParameter("id", commande.getId())
                    .getSingleResult();
            commande.setStockRestitue(stockRestitueFrais);
        }

        if (Boolean.TRUE.equals(commande.getStockRestitue())) {
            return;
        }

        if (commande.getLignesCommande() != null) {
            // Agrège les quantités par plat, comme CommandeServiceImpl le fait avant reserver() :
            // une commande peut porter plusieurs lignes du même produit (options différentes) et
            // ne doit être re-créditée qu'une fois par produit, avec la quantité cumulée. TreeMap
            // trie par platId croissant : même ordre de verrouillage que reserver()/
            // CommandeServiceImpl, indispensable pour qu'une annulation concurrente d'une autre
            // commande partageant des produits ne réintroduise pas de risque d'interblocage.
            Map<Long, Integer> quantitesParPlat = new TreeMap<>();
            for (LigneCommande ligne : commande.getLignesCommande()) {
                if (ligne.getPlat() == null || ligne.getPlat().getId() == null) {
                    continue;
                }
                quantitesParPlat.merge(ligne.getPlat().getId(), ligne.getQuantite(), Integer::sum);
            }

            for (Map.Entry<Long, Integer> entry : quantitesParPlat.entrySet()) {
                Long platId = entry.getKey();
                int quantite = entry.getValue();

                // Même discipline que reserver() : pré-lecture non verrouillante, pour ne jamais
                // prendre de verrou sur un plat à stock non géré (100% des plats de restaurant).
                Plat platNonVerrouille = platRepository.findById(platId).orElse(null);
                if (platNonVerrouille == null || platNonVerrouille.getQuantiteStock() == null) {
                    continue; // produit supprimé, ou stock non géré : rien à restituer
                }

                Plat plat = platRepository.findByIdForUpdate(platId).orElse(null);
                if (plat == null) {
                    continue; // produit supprimé entre les deux lectures : l'annulation doit
                    // quand même aboutir, on ne fait pas échouer la commande pour ça
                }

                // Même piège Hibernate que reserver() : le SELECT ... FOR UPDATE acquiert le
                // verrou en base mais Hibernate renvoie l'instance déjà managée sans la
                // réhydrater. Sans ce refresh(), une restitution concurrente sur le même plat
                // peut repartir d'une lecture non verrouillée obsolète et écraser silencieusement
                // le crédit de l'autre transaction.
                entityManager.refresh(plat);

                Integer stockActuel = plat.getQuantiteStock();
                if (stockActuel == null) {
                    continue; // redevenu non géré entre les deux lectures (rarissime)
                }
                plat.setQuantiteStock(stockActuel + quantite);
                platRepository.save(plat);
            }
        }

        commande.setStockRestitue(true);
        log.info("Stock restitue pour la commande {}", commande.getNumeroCommande());
    }
}
