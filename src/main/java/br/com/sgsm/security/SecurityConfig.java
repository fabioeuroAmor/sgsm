package br.com.sgsm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Swagger
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Auto-cadastro publico: novo medico/paciente cria seu perfil antes de autenticar
                .requestMatchers(HttpMethod.POST, "/v1/api/medicos").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/api/pacientes").permitAll()
                // Medicos: MEDICO so ve a si mesmo; FUNCIONARIO e DESENVOLVEDOR veem todos
                .requestMatchers(HttpMethod.GET, "/v1/api/medicos/**")
                    .hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers(HttpMethod.POST, "/v1/api/medicos/**")
                    .hasAnyRole("FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers(HttpMethod.PUT, "/v1/api/medicos/**")
                    .hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers(HttpMethod.DELETE, "/v1/api/medicos/**")
                    .hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR")
                // Pacientes: leitura para todos os roles; escrita apenas para PACIENTE (próprios dados), FUNCIONARIO e DESENVOLVEDOR
                .requestMatchers(HttpMethod.GET, "/v1/api/pacientes/**")
                    .hasAnyRole("PACIENTE", "MEDICO", "FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers(HttpMethod.POST, "/v1/api/pacientes/**")
                    .hasAnyRole("FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers(HttpMethod.PUT, "/v1/api/pacientes/**")
                    .hasAnyRole("PACIENTE", "FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers(HttpMethod.DELETE, "/v1/api/pacientes/**")
                    .hasAnyRole("FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers(HttpMethod.PATCH, "/v1/api/pacientes/**")
                    .hasAnyRole("FUNCIONARIO", "DESENVOLVEDOR")
                // Agendamentos: todos autenticados; segregacao feita no Service
                .requestMatchers("/v1/api/agendamentos/**").authenticated()
                // Agenda e servicos
                .requestMatchers("/v1/api/agenda/**").hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers("/v1/api/servicos/**").hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR")
                .requestMatchers("/v1/api/estabelecimentos/**").hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR")
                // Funcionarios: MEDICO gerencia apenas funcionarios dos seus estabelecimentos
                .requestMatchers("/v1/api/funcionarios/**").hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
