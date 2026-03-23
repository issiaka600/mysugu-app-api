package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.stats.*;

import java.time.LocalDate;
import java.util.List;

public interface StatistiquesService {

    // Dashboard
    DashboardOverviewDTO getDashboardOverview();

    // Évolution commandes
    List<EvolutionCommandesDTO> getEvolutionCommandesParJour(LocalDate debut, LocalDate fin);
    List<EvolutionCommandesDTO> getEvolutionCommandesParMois(int annee);

    // Répartitions commandes
    List<CommandesParStatutDTO> getCommandesParStatut(LocalDate debut, LocalDate fin);
    List<CommandesParModeDTO> getCommandesParMode(LocalDate debut, LocalDate fin);
    List<CommandesParPaiementDTO> getCommandesParMethodePaiement(LocalDate debut, LocalDate fin);
    List<HeurePointe> getHeuresPointe(LocalDate debut, LocalDate fin);

    // Analytics restaurants
    List<TopRestaurantDTO> getTopRestaurantsParCommandes(int limit, LocalDate debut, LocalDate fin);
    List<TopRestaurantDTO> getTopRestaurantsParCA(int limit, LocalDate debut, LocalDate fin);
    List<RestaurantPerformanceDTO> getRestaurantsPerformance(LocalDate debut, LocalDate fin);

    // Analytics clients
    List<ClientAnalyticsDTO> getTopClients(int limit, LocalDate debut, LocalDate fin);
    RetentionDTO getRetentionClients();
    List<ZoneCommandesDTO> getCommandesParVille(LocalDate debut, LocalDate fin);

    // Analytics livreurs
    List<LivreurAnalyticsDTO> getLivreursPerformance(LocalDate debut, LocalDate fin);
    List<LivreurAnalyticsDTO> getTopLivreurs(int limit, LocalDate debut, LocalDate fin);

    // Analytics plats
    List<PlatAnalyticsDTO> getTopPlats(int limit, LocalDate debut, LocalDate fin);
    List<PlatAnalyticsDTO> getPlatsJamaisCommandes();
    List<PlatAnalyticsDTO> getCAParCategoriePlat(LocalDate debut, LocalDate fin);

    // Finance
    FinancierDTO getRapportFinancier(LocalDate debut, LocalDate fin);

    // Monitoring temps réel
    MonitoringTempsReelDTO getMonitoringTempsReel();
    List<AlerteDTO> getAlertes();
}
