package ma.mysuguclientapp.config.security;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    // Endpoints non protégés (login/register, docs, health, etc.) — Ant patterns
    private static final List<String> SECURITY_WHITELIST = List.of(
            "/api/auth/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/actuator/health"
    );


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> 
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Routes publiques
//                        .requestMatchers("/api/users/register", "/api/users/login").permitAll()
                        .requestMatchers("/auth/**", "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/health"
                        ).permitAll()

                        // Routes publiques en lecture seule
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/restaurants/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/plats/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()

                        // Routes admin uniquement
                        .requestMatchers("/api/users/admin/**").hasRole("ADMIN")
                        
                        // Routes propriétaires de restaurant
                        .requestMatchers(HttpMethod.POST, "/api/restaurants").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/restaurants/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/restaurants/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        
                        .requestMatchers(HttpMethod.POST, "/api/plats").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/plats/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/plats/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        
                        // Routes catégories (admin)
                        .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                        
                        // Routes commandes
                        .requestMatchers(HttpMethod.POST, "/api/commandes").hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/commandes/client/**").hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/commandes/restaurant/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/commandes/livreur/**").hasAnyRole("LIVREUR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/commandes/{commandeId}/status").hasAnyRole("RESTAURANT_OWNER", "LIVREUR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/commandes/{commandeId}/assign-livreur/**").hasAnyRole("ADMIN", "RESTAURANT_OWNER")
                        
                        // Toutes les autres routes nécessitent une authentification
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:4200", "*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
