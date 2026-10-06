package pl.srm.registrationapi.common.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

@Configuration
@EnableWebSecurity
public class ManagementSecurityConfig {

    @Bean
    public UserDetailsService serviceUsers(
            @Value("${registration.service.username}") String username,
            @Value("${registration.service.password}") String password) {
        Assert.hasText(username, "Registration service username must not be blank");
        Assert.hasText(password, "Registration service password must not be blank");
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password))
                .roles("REGISTRATION_MANAGEMENT")
                .build());
    }

    @Bean
    public SecurityFilterChain registrationSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/registrations/participant", "/api/registrations/staff").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/turnuses", "/api/turnusy", "/actuator/health").permitAll()
                        .requestMatchers("/api/registrations", "/api/registrations/**")
                                .hasRole("REGISTRATION_MANAGEMENT")
                        .anyRequest().denyAll())
                .build();
    }
}
