package br.com.sgsm.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
            // Sem token = 401 (não 403): o front só tenta refresh/redireciona para o login em 401.
            // 403 fica para quem está autenticado mas não tem o papel exigido.
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                // Swagger
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // /error precisa ser liberado: response.sendError() no JwtAuthFilter dispara um
                // forward interno pra cá, que reentra na cadeia de segurança sem token válido —
                // sem isso, o forward era barrado com 403 e sobrescrevia o 401 original.
                .requestMatchers("/error").permitAll()
                // Auto-cadastro público de paciente (limite por IP e ativação por e-mail no service)
                .requestMatchers(HttpMethod.POST, "/v1/api/publico/pacientes").permitAll()
                // Medicos: MEDICO so ve a si mesmo; FUNCIONARIO, ADMIN_ESTABELECIMENTO e
                // DESENVOLVEDOR veem todos (escopados por estabelecimento no Service,
                // exceto DESENVOLVEDOR); PACIENTE precisa listar para escolher medico/
                // servico no agendamento
                .requestMatchers(HttpMethod.GET, "/v1/api/medicos/**")
                    .hasAnyRole("PACIENTE", "MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers(HttpMethod.POST, "/v1/api/medicos/**")
                    .hasAnyRole("FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers(HttpMethod.PUT, "/v1/api/medicos/**")
                    .hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers(HttpMethod.DELETE, "/v1/api/medicos/**")
                    .hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers(HttpMethod.PATCH, "/v1/api/medicos/**")
                    .hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                // Pacientes: leitura para todos os roles; escrita apenas para PACIENTE (próprios dados), FUNCIONARIO, ADMIN_ESTABELECIMENTO e DESENVOLVEDOR
                .requestMatchers(HttpMethod.GET, "/v1/api/pacientes/**")
                    .hasAnyRole("PACIENTE", "MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers(HttpMethod.POST, "/v1/api/pacientes/**")
                    .hasAnyRole("FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers(HttpMethod.PUT, "/v1/api/pacientes/**")
                    .hasAnyRole("PACIENTE", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers(HttpMethod.DELETE, "/v1/api/pacientes/**")
                    .hasAnyRole("FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers(HttpMethod.PATCH, "/v1/api/pacientes/**")
                    .hasAnyRole("FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                // Agendamentos: todos autenticados; segregacao feita no Service
                .requestMatchers("/v1/api/agendamentos/**").authenticated()
                // Agenda e servicos
                .requestMatchers("/v1/api/agenda-medico/**").hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                // Servicos medicos: leitura tambem para PACIENTE (escolher servico no agendamento);
                // escrita continua restrita a staff
                .requestMatchers(HttpMethod.GET, "/v1/api/servicos-medicos/**")
                    .hasAnyRole("PACIENTE", "MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers("/v1/api/servicos-medicos/**").hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                .requestMatchers("/v1/api/estabelecimentos/**").hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                // Funcionarios: MEDICO, FUNCIONARIO e ADMIN_ESTABELECIMENTO gerenciam apenas
                // funcionarios dos seus proprios estabelecimentos (escopo no Service)
                .requestMatchers("/v1/api/funcionarios/**").hasAnyRole("MEDICO", "FUNCIONARIO", "DESENVOLVEDOR", "ADMIN_ESTABELECIMENTO")
                // Administradores: gerenciados só por DESENVOLVEDOR (criação do perfil
                // e vínculo a estabelecimento não é self-service)
                .requestMatchers("/v1/api/administradores/**").hasRole("DESENVOLVEDOR")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
