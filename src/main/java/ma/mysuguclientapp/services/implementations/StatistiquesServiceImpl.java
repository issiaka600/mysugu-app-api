package ma.mysuguclientapp.services.implementations;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.stats.*;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.services.interfaces.StatistiquesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StatistiquesServiceImpl implements StatistiquesService {

    private final EntityManager em;

    // ==================== DASHBOARD ====================

    @Override
    public DashboardOverviewDTO getDashboardOverview() {
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        LocalDateTime finJour = debutJour.plusDays(1);
        LocalDateTime debutSemaine = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime debutMois = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return DashboardOverviewDTO.builder()
                .commandesTotalAujourdhui(countCommandes(debutJour, finJour))
                .commandesTotalSemaine(countCommandes(debutSemaine, finJour))
                .commandesTotalMois(countCommandes(debutMois, finJour))
                .chiffreAffairesAujourdhui(sumCA(debutJour, finJour))
                .chiffreAffairesSemaine(sumCA(debutSemaine, finJour))
                .chiffreAffairesMois(sumCA(debutMois, finJour))
                .tauxAnnulation(calculerTauxAnnulation(debutMois, finJour))
                .valeurMoyenneCommande(calculerValeurMoyenne(debutMois, finJour))
                .nouveauxUsersAujourdhui(countNouveauxUsers(debutJour, finJour))
                .nouveauxUsersSemaine(countNouveauxUsers(debutSemaine, finJour))
                .nouveauxUsersMois(countNouveauxUsers(debutMois, finJour))
                .commandesEnCours(countCommandesEnCours())
                .restaurantsActifs(countRestaurantsActifs())
                .livreursActifs(countLivreursActifs())
                .build();
    }

    private Long countCommandes(LocalDateTime debut, LocalDateTime fin) {
        return em.createQuery(
                "SELECT COUNT(c) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt < :fin AND c.statut != :annulee",
                Long.class)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .setParameter("annulee", StatutCommande.ANNULEE)
                .getSingleResult();
    }

    private BigDecimal sumCA(LocalDateTime debut, LocalDateTime fin) {
        Object result = em.createQuery(
                "SELECT SUM(c.montantTotal) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt < :fin AND c.statut = :livree")
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .setParameter("livree", StatutCommande.LIVREE)
                .getSingleResult();
        if (result == null) return BigDecimal.ZERO;
        return new BigDecimal(result.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private Double calculerTauxAnnulation(LocalDateTime debut, LocalDateTime fin) {
        Long total = em.createQuery(
                "SELECT COUNT(c) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt < :fin",
                Long.class)
                .setParameter("debut", debut).setParameter("fin", fin).getSingleResult();
        if (total == 0) return 0.0;
        Long annulees = em.createQuery(
                "SELECT COUNT(c) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt < :fin AND c.statut = :annulee",
                Long.class)
                .setParameter("debut", debut).setParameter("fin", fin)
                .setParameter("annulee", StatutCommande.ANNULEE).getSingleResult();
        return Math.round((annulees * 100.0 / total) * 100.0) / 100.0;
    }

    private BigDecimal calculerValeurMoyenne(LocalDateTime debut, LocalDateTime fin) {
        Object result = em.createQuery(
                "SELECT AVG(c.montantTotal) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt < :fin AND c.statut = :livree")
                .setParameter("debut", debut).setParameter("fin", fin)
                .setParameter("livree", StatutCommande.LIVREE).getSingleResult();
        if (result == null) return BigDecimal.ZERO;
        return new BigDecimal(result.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private Long countNouveauxUsers(LocalDateTime debut, LocalDateTime fin) {
        return em.createQuery(
                "SELECT COUNT(u) FROM User u WHERE u.createdAt >= :debut AND u.createdAt < :fin AND u.role = :role",
                Long.class)
                .setParameter("debut", debut).setParameter("fin", fin)
                .setParameter("role", UserRole.CLIENT).getSingleResult();
    }

    private Long countCommandesEnCours() {
        List<StatutCommande> statuts = Arrays.asList(
                StatutCommande.EN_ATTENTE, StatutCommande.CONFIRMEE,
                StatutCommande.EN_PREPARATION, StatutCommande.PRETE, StatutCommande.EN_COURS);
        return em.createQuery(
                "SELECT COUNT(c) FROM Commande c WHERE c.statut IN :statuts", Long.class)
                .setParameter("statuts", statuts).getSingleResult();
    }

    private Long countRestaurantsActifs() {
        return em.createQuery("SELECT COUNT(r) FROM Restaurant r WHERE r.isActive = true", Long.class)
                .getSingleResult();
    }

    private Long countLivreursActifs() {
        return em.createQuery(
                "SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.isActive = true", Long.class)
                .setParameter("role", UserRole.LIVREUR).getSingleResult();
    }

    // ==================== ÉVOLUTION COMMANDES ====================

    @Override
    public List<EvolutionCommandesDTO> getEvolutionCommandesParJour(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT cast(c.createdAt as date), COUNT(c), " +
                "SUM(CASE WHEN c.statut = :livree THEN c.montantTotal ELSE 0 END), " +
                "SUM(CASE WHEN c.statut = :livree THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN c.statut = :annulee THEN 1 ELSE 0 END) " +
                "FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin " +
                "GROUP BY cast(c.createdAt as date) ORDER BY cast(c.createdAt as date)",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .setParameter("annulee", StatutCommande.ANNULEE)
                .getResultList();

        return results.stream().map(row -> EvolutionCommandesDTO.builder()
                .periode(row[0] != null ? row[0].toString() : "")
                .nombreCommandes(((Number) row[1]).longValue())
                .chiffreAffaires(row[2] != null ? new BigDecimal(row[2].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .commandesLivrees(row[3] != null ? ((Number) row[3]).longValue() : 0L)
                .commandesAnnulees(row[4] != null ? ((Number) row[4]).longValue() : 0L)
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<EvolutionCommandesDTO> getEvolutionCommandesParMois(int annee) {
        List<Object[]> results = em.createQuery(
                "SELECT extract(month from c.createdAt), COUNT(c), " +
                "SUM(CASE WHEN c.statut = :livree THEN c.montantTotal ELSE 0 END), " +
                "SUM(CASE WHEN c.statut = :livree THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN c.statut = :annulee THEN 1 ELSE 0 END) " +
                "FROM Commande c WHERE extract(year from c.createdAt) = :annee " +
                "GROUP BY extract(month from c.createdAt) ORDER BY extract(month from c.createdAt)",
                Object[].class)
                .setParameter("annee", annee)
                .setParameter("livree", StatutCommande.LIVREE)
                .setParameter("annulee", StatutCommande.ANNULEE)
                .getResultList();

        return results.stream().map(row -> EvolutionCommandesDTO.builder()
                .periode(annee + "-" + String.format("%02d", ((Number) row[0]).intValue()))
                .nombreCommandes(((Number) row[1]).longValue())
                .chiffreAffaires(row[2] != null ? new BigDecimal(row[2].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .commandesLivrees(row[3] != null ? ((Number) row[3]).longValue() : 0L)
                .commandesAnnulees(row[4] != null ? ((Number) row[4]).longValue() : 0L)
                .build()).collect(Collectors.toList());
    }

    // ==================== RÉPARTITIONS ====================

    @Override
    public List<CommandesParStatutDTO> getCommandesParStatut(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.statut, COUNT(c) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin GROUP BY c.statut",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .getResultList();

        long total = results.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        return results.stream().map(row -> {
            long count = ((Number) row[1]).longValue();
            return CommandesParStatutDTO.builder()
                    .statut(row[0].toString())
                    .nombre(count)
                    .pourcentage(total > 0 ? Math.round(count * 100.0 / total * 100.0) / 100.0 : 0.0)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<CommandesParModeDTO> getCommandesParMode(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.modeReception, COUNT(c) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin GROUP BY c.modeReception",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .getResultList();

        long total = results.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        return results.stream().map(row -> {
            long count = ((Number) row[1]).longValue();
            return CommandesParModeDTO.builder()
                    .modeReception(row[0] != null ? row[0].toString() : "INCONNU")
                    .nombre(count)
                    .pourcentage(total > 0 ? Math.round(count * 100.0 / total * 100.0) / 100.0 : 0.0)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<CommandesParPaiementDTO> getCommandesParMethodePaiement(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.methodePaiement, COUNT(c) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin AND c.methodePaiement IS NOT NULL GROUP BY c.methodePaiement",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .getResultList();

        long total = results.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        return results.stream().map(row -> {
            long count = ((Number) row[1]).longValue();
            return CommandesParPaiementDTO.builder()
                    .methodePaiement(row[0] != null ? row[0].toString() : "INCONNU")
                    .nombre(count)
                    .pourcentage(total > 0 ? Math.round(count * 100.0 / total * 100.0) / 100.0 : 0.0)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<HeurePointe> getHeuresPointe(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT extract(hour from c.createdAt), extract(dow from c.createdAt), COUNT(c) " +
                "FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin " +
                "GROUP BY extract(hour from c.createdAt), extract(dow from c.createdAt) " +
                "ORDER BY COUNT(c) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .getResultList();

        return results.stream().map(row -> HeurePointe.builder()
                .heure(((Number) row[0]).intValue())
                .jourSemaine(((Number) row[1]).intValue())
                .nombreCommandes(((Number) row[2]).longValue())
                .build()).collect(Collectors.toList());
    }

    // ==================== ANALYTICS RESTAURANTS ====================

    @Override
    public List<TopRestaurantDTO> getTopRestaurantsParCommandes(int limit, LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.restaurant.id, c.restaurant.nom, COUNT(c), SUM(CASE WHEN c.statut = :livree THEN c.montantTotal ELSE 0 END), " +
                "c.restaurant.appreciation, c.restaurant.nombreAvis " +
                "FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin " +
                "GROUP BY c.restaurant.id, c.restaurant.nom, c.restaurant.appreciation, c.restaurant.nombreAvis " +
                "ORDER BY COUNT(c) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .setMaxResults(limit)
                .getResultList();

        return buildTopRestaurantList(results);
    }

    @Override
    public List<TopRestaurantDTO> getTopRestaurantsParCA(int limit, LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.restaurant.id, c.restaurant.nom, COUNT(c), SUM(CASE WHEN c.statut = :livree THEN c.montantTotal ELSE 0 END), " +
                "c.restaurant.appreciation, c.restaurant.nombreAvis " +
                "FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin " +
                "GROUP BY c.restaurant.id, c.restaurant.nom, c.restaurant.appreciation, c.restaurant.nombreAvis " +
                "ORDER BY SUM(CASE WHEN c.statut = :livree THEN c.montantTotal ELSE 0 END) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .setMaxResults(limit)
                .getResultList();

        return buildTopRestaurantList(results);
    }

    private List<TopRestaurantDTO> buildTopRestaurantList(List<Object[]> results) {
        return results.stream().map(row -> {
            Long total = ((Number) row[2]).longValue();
            return TopRestaurantDTO.builder()
                    .restaurantId(((Number) row[0]).longValue())
                    .nom((String) row[1])
                    .nombreCommandes(total)
                    .chiffreAffaires(row[3] != null ? new BigDecimal(row[3].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .appreciation(row[4] != null ? ((Number) row[4]).doubleValue() : 0.0)
                    .nombreAvis(row[5] != null ? ((Number) row[5]).longValue() : 0L)
                    .tauxValidation(0.0)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<RestaurantPerformanceDTO> getRestaurantsPerformance(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.restaurant.id, c.restaurant.nom, COUNT(c), " +
                "SUM(CASE WHEN c.statut = :livree THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN c.statut = :annulee THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN c.statut = :livree THEN c.montantTotal ELSE 0 END) " +
                "FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin " +
                "GROUP BY c.restaurant.id, c.restaurant.nom ORDER BY COUNT(c) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .setParameter("annulee", StatutCommande.ANNULEE)
                .getResultList();

        return results.stream().map(row -> {
            long total = ((Number) row[2]).longValue();
            long annulees = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            return RestaurantPerformanceDTO.builder()
                    .restaurantId(((Number) row[0]).longValue())
                    .nom((String) row[1])
                    .nombreCommandes(total)
                    .commandesLivrees(row[3] != null ? ((Number) row[3]).longValue() : 0L)
                    .commandesAnnulees(annulees)
                    .tauxAnnulation(total > 0 ? Math.round(annulees * 100.0 / total * 100.0) / 100.0 : 0.0)
                    .chiffreAffaires(row[5] != null ? new BigDecimal(row[5].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .tempsPreparationMoyen(0.0)
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================== ANALYTICS CLIENTS ====================

    @Override
    public List<ClientAnalyticsDTO> getTopClients(int limit, LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.client.id, c.client.nom, c.client.prenom, c.client.email, " +
                "COUNT(c), SUM(c.montantTotal), MIN(c.createdAt), MAX(c.createdAt) " +
                "FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin AND c.statut = :livree " +
                "GROUP BY c.client.id, c.client.nom, c.client.prenom, c.client.email " +
                "ORDER BY SUM(c.montantTotal) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .setMaxResults(limit)
                .getResultList();

        return results.stream().map(row -> {
            LocalDateTime derniere = (LocalDateTime) row[7];
            long jours = derniere != null ? ChronoUnit.DAYS.between(derniere.toLocalDate(), LocalDate.now()) : 0;
            return ClientAnalyticsDTO.builder()
                    .clientId(((Number) row[0]).longValue())
                    .nom((String) row[1])
                    .prenom((String) row[2])
                    .email((String) row[3])
                    .nombreCommandes(((Number) row[4]).longValue())
                    .totalDepense(row[5] != null ? new BigDecimal(row[5].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .premiereCommande((LocalDateTime) row[6])
                    .derniereCommande(derniere)
                    .joursDepuisDerniereCommande(jours)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public RetentionDTO getRetentionClients() {
        Long totalClients = em.createQuery(
                "SELECT COUNT(DISTINCT c.client.id) FROM Commande c", Long.class).getSingleResult();

        List<Object[]> commandesParClient = em.createQuery(
                "SELECT c.client.id, COUNT(c) FROM Commande c WHERE c.statut = :livree GROUP BY c.client.id",
                Object[].class)
                .setParameter("livree", StatutCommande.LIVREE)
                .getResultList();

        long avec1 = commandesParClient.stream().filter(r -> ((Number) r[1]).longValue() == 1).count();
        long avec2Plus = commandesParClient.stream().filter(r -> ((Number) r[1]).longValue() >= 2).count();
        long total = commandesParClient.size();

        return RetentionDTO.builder()
                .totalClients(totalClients)
                .clientsAvec1Commande(avec1)
                .clientsAvec2PlusCommandes(avec2Plus)
                .tauxRetention(total > 0 ? Math.round(avec2Plus * 100.0 / total * 100.0) / 100.0 : 0.0)
                .tauxAbandon(total > 0 ? Math.round(avec1 * 100.0 / total * 100.0) / 100.0 : 0.0)
                .build();
    }

    @Override
    public List<ZoneCommandesDTO> getCommandesParVille(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.adresseLivraison.ville, COUNT(c), SUM(CASE WHEN c.statut = :livree THEN c.montantTotal ELSE 0 END) " +
                "FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin AND c.adresseLivraison.ville IS NOT NULL " +
                "GROUP BY c.adresseLivraison.ville ORDER BY COUNT(c) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .getResultList();

        long totalCommandes = results.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        return results.stream().map(row -> {
            long count = ((Number) row[1]).longValue();
            return ZoneCommandesDTO.builder()
                    .ville((String) row[0])
                    .nombreCommandes(count)
                    .chiffreAffaires(row[2] != null ? new BigDecimal(row[2].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .pourcentage(totalCommandes > 0 ? Math.round(count * 100.0 / totalCommandes * 100.0) / 100.0 : 0.0)
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================== ANALYTICS LIVREURS ====================

    @Override
    public List<LivreurAnalyticsDTO> getLivreursPerformance(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT c.livreur.id, c.livreur.nom, c.livreur.prenom, COUNT(c), SUM(c.fraisLivraison) " +
                "FROM Commande c WHERE c.livreur IS NOT NULL AND c.statut = :livree " +
                "AND c.createdAt >= :debut AND c.createdAt <= :fin " +
                "GROUP BY c.livreur.id, c.livreur.nom, c.livreur.prenom ORDER BY COUNT(c) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .getResultList();

        return results.stream().map(row -> LivreurAnalyticsDTO.builder()
                .livreurId(((Number) row[0]).longValue())
                .nom((String) row[1])
                .prenom((String) row[2])
                .nombreLivraisons(((Number) row[3]).longValue())
                .totalFraisLivraison(row[4] != null ? new BigDecimal(row[4].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .tempsLivraisonMoyen(0.0)
                .distanceTotaleParcourue(0.0)
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<LivreurAnalyticsDTO> getTopLivreurs(int limit, LocalDate debut, LocalDate fin) {
        return getLivreursPerformance(debut, fin).stream().limit(limit).collect(Collectors.toList());
    }

    // ==================== ANALYTICS PLATS ====================

    @Override
    public List<PlatAnalyticsDTO> getTopPlats(int limit, LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT lc.plat.id, lc.plat.nom, lc.plat.restaurant.nom, lc.plat.categoriePlat, " +
                "COUNT(lc), SUM(lc.quantite), SUM(lc.montantTotal) " +
                "FROM LigneCommande lc JOIN lc.commande c " +
                "WHERE c.createdAt >= :debut AND c.createdAt <= :fin AND c.statut = :livree " +
                "GROUP BY lc.plat.id, lc.plat.nom, lc.plat.restaurant.nom, lc.plat.categoriePlat " +
                "ORDER BY SUM(lc.quantite) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .setMaxResults(limit)
                .getResultList();

        return results.stream().map(row -> PlatAnalyticsDTO.builder()
                .platId(((Number) row[0]).longValue())
                .nom((String) row[1])
                .restaurantNom((String) row[2])
                .categorie(row[3] != null ? row[3].toString() : "")
                .nombreCommandes(((Number) row[4]).longValue())
                .quantiteTotale(row[5] != null ? ((Number) row[5]).longValue() : 0L)
                .chiffreAffaires(row[6] != null ? new BigDecimal(row[6].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<PlatAnalyticsDTO> getPlatsJamaisCommandes() {
        List<Object[]> results = em.createQuery(
                "SELECT p.id, p.nom, p.restaurant.nom, p.categoriePlat " +
                "FROM Plat p WHERE p.id NOT IN " +
                "(SELECT DISTINCT lc.plat.id FROM LigneCommande lc)",
                Object[].class)
                .getResultList();

        return results.stream().map(row -> PlatAnalyticsDTO.builder()
                .platId(((Number) row[0]).longValue())
                .nom((String) row[1])
                .restaurantNom((String) row[2])
                .categorie(row[3] != null ? row[3].toString() : "")
                .nombreCommandes(0L)
                .quantiteTotale(0L)
                .chiffreAffaires(BigDecimal.ZERO)
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<PlatAnalyticsDTO> getCAParCategoriePlat(LocalDate debut, LocalDate fin) {
        List<Object[]> results = em.createQuery(
                "SELECT lc.plat.categoriePlat, COUNT(lc), SUM(lc.quantite), SUM(lc.montantTotal) " +
                "FROM LigneCommande lc JOIN lc.commande c " +
                "WHERE c.createdAt >= :debut AND c.createdAt <= :fin AND c.statut = :livree " +
                "GROUP BY lc.plat.categoriePlat ORDER BY SUM(lc.montantTotal) DESC",
                Object[].class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .getResultList();

        return results.stream().map(row -> PlatAnalyticsDTO.builder()
                .categorie(row[0] != null ? row[0].toString() : "AUTRE")
                .nombreCommandes(((Number) row[1]).longValue())
                .quantiteTotale(row[2] != null ? ((Number) row[2]).longValue() : 0L)
                .chiffreAffaires(row[3] != null ? new BigDecimal(row[3].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .build()).collect(Collectors.toList());
    }

    // ==================== FINANCE ====================

    @Override
    public FinancierDTO getRapportFinancier(LocalDate debut, LocalDate fin) {
        Object[] result = (Object[]) em.createQuery(
                "SELECT COALESCE(SUM(c.montantTotal), 0), COALESCE(SUM(c.fraisLivraison), 0), COUNT(c) " +
                "FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin AND c.statut = :livree")
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("livree", StatutCommande.LIVREE)
                .getSingleResult();

        Long commandesRembourse = em.createQuery(
                "SELECT COUNT(c) FROM Commande c WHERE c.createdAt >= :debut AND c.createdAt <= :fin AND c.statutPaiement = :rembourse",
                Long.class)
                .setParameter("debut", debut.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .setParameter("rembourse", StatutPaiement.REMBOURSE)
                .getSingleResult();

        BigDecimal revenuBrut = result[0] != null ? new BigDecimal(result[0].toString()) : BigDecimal.ZERO;
        BigDecimal fraisLivraison = result[1] != null ? new BigDecimal(result[1].toString()) : BigDecimal.ZERO;
        BigDecimal commission = revenuBrut.multiply(BigDecimal.valueOf(0.15)).setScale(2, RoundingMode.HALF_UP);

        return FinancierDTO.builder()
                .revenuBrut(revenuBrut.setScale(2, RoundingMode.HALF_UP))
                .totalFraisLivraison(fraisLivraison.setScale(2, RoundingMode.HALF_UP))
                .commissionPlateforme(commission)
                .revenuNet(revenuBrut.subtract(commission).setScale(2, RoundingMode.HALF_UP))
                .commandesPaye(result[2] != null ? ((Number) result[2]).longValue() : 0L)
                .commandesRembourse(commandesRembourse)
                .build();
    }

    // ==================== MONITORING TEMPS RÉEL ====================

    @Override
    public MonitoringTempsReelDTO getMonitoringTempsReel() {
        List<AlerteDTO> alertes = getAlertes();
        List<CommandeEnCoursDTO> enCours = getCommandesEnCoursDTO();
        return MonitoringTempsReelDTO.builder()
                .commandesEnCours(enCours)
                .alertes(alertes)
                .totalCommandesEnCours((long) enCours.size())
                .build();
    }

    @Override
    public List<AlerteDTO> getAlertes() {
        List<AlerteDTO> alertes = new ArrayList<>();
        LocalDateTime seuilAttente = LocalDateTime.now().minusMinutes(30);

        List<Object[]> commandesEnAttente = em.createQuery(
                "SELECT c.id, c.numeroCommande, c.restaurant.nom, c.createdAt " +
                "FROM Commande c WHERE c.statut = :statut AND c.createdAt <= :seuil",
                Object[].class)
                .setParameter("statut", StatutCommande.EN_ATTENTE)
                .setParameter("seuil", seuilAttente)
                .getResultList();

        commandesEnAttente.forEach(row -> alertes.add(AlerteDTO.builder()
                .type("COMMANDE_EN_ATTENTE_LONGUE")
                .message("Commande " + row[1] + " en attente depuis plus de 30 minutes")
                .entityId(((Number) row[0]).longValue())
                .entityNom((String) row[2])
                .detectedAt(LocalDateTime.now())
                .build()));

        return alertes;
    }

    private List<CommandeEnCoursDTO> getCommandesEnCoursDTO() {
        List<StatutCommande> statuts = Arrays.asList(
                StatutCommande.EN_ATTENTE, StatutCommande.CONFIRMEE,
                StatutCommande.EN_PREPARATION, StatutCommande.PRETE, StatutCommande.EN_COURS);

        List<Object[]> results = em.createQuery(
                "SELECT c.id, c.numeroCommande, c.statut, c.restaurant.nom, " +
                "c.client.nom, c.client.prenom, c.createdAt " +
                "FROM Commande c WHERE c.statut IN :statuts ORDER BY c.createdAt ASC",
                Object[].class)
                .setParameter("statuts", statuts)
                .getResultList();

        return results.stream().map(row -> {
            LocalDateTime createdAt = (LocalDateTime) row[6];
            long minutes = createdAt != null ? ChronoUnit.MINUTES.between(createdAt, LocalDateTime.now()) : 0;
            return CommandeEnCoursDTO.builder()
                    .commandeId(((Number) row[0]).longValue())
                    .numeroCommande((String) row[1])
                    .statut(row[2].toString())
                    .restaurantNom((String) row[3])
                    .clientNom(row[4] + " " + row[5])
                    .minutesDepuisCreation(minutes)
                    .enRetard(minutes > 60)
                    .build();
        }).collect(Collectors.toList());
    }
}
