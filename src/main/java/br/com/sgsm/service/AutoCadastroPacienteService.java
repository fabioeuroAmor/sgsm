package br.com.sgsm.service;

import br.com.sgsm.client.AuthServiceClient;
import br.com.sgsm.dto.AutoCadastroPacienteRequest;
import br.com.sgsm.dto.AutoCadastroPacienteResponse;
import br.com.sgsm.dto.CadastrarPacienteRequest;
import br.com.sgsm.exception.CadastroIndisponivelException;
import br.com.sgsm.exception.MuitasTentativasException;
import br.com.sgsm.exception.ServicoIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

// Auto-cadastro público de paciente pelo site (sem login). Proteções:
// - limite de cadastros por IP (Redis), fail-closed: sem Redis não há como limitar, então recusa;
// - CPF ou e-mail já existentes recebem a MESMA resposta (não revela quem tem cadastro);
// - o login é criado aqui mesmo no auth (servidor a servidor) e nasce inativo, liberado só pelo
//   código enviado ao e-mail; se o auth falhar, o paciente recém-criado é desfeito (sem órfão);
// - o id do paciente não volta para o navegador.
// Sem @Transactional: o paciente precisa estar commitado para o auth enxergá-lo (view entidade_auth).
@Service
public class AutoCadastroPacienteService {

    private static final Logger log = LoggerFactory.getLogger(AutoCadastroPacienteService.class);

    static final String PREFIXO_IP = "sgsm:autocadastro:ip:";
    static final Duration JANELA = Duration.ofHours(1);
    static final int TAMANHO_MINIMO_SENHA = 8;
    static final String MENSAGEM_DUPLICADO = "Não foi possível concluir o cadastro com estes dados. "
            + "Se você já tem cadastro, faça login ou use \"Esqueci minha senha\".";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final PacienteService pacienteService;
    private final AuthServiceClient authServiceClient;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final int limitePorIp;

    public AutoCadastroPacienteService(PacienteService pacienteService,
                                       AuthServiceClient authServiceClient,
                                       StringRedisTemplate redis,
                                       Clock clock,
                                       @Value("${sgsm.autocadastro.limite-por-ip:5}") int limitePorIp) {
        this.pacienteService = pacienteService;
        this.authServiceClient = authServiceClient;
        this.redis = redis;
        this.clock = clock;
        this.limitePorIp = limitePorIp;
    }

    public AutoCadastroPacienteResponse cadastrar(AutoCadastroPacienteRequest request, String ip) {
        limitarPorIp(ip);
        validar(request);
        String email = request.email().trim();

        if (pacienteService.cpfOuEmailJaCadastrado(request.cpf(), email)) {
            throw new CadastroIndisponivelException(MENSAGEM_DUPLICADO);
        }

        UUID pacienteId;
        try {
            pacienteId = pacienteService.cadastrar(new CadastrarPacienteRequest(
                    request.nome().trim(), request.cpf(), request.dataNascimento(), email,
                    request.telefone(), null, null, null, null, null, null, null,
                    request.consentimentoLgpd())).getId();
        } catch (DataIntegrityViolationException e) {
            // corrida: outro cadastro com o mesmo CPF/e-mail commitou entre a checagem e o insert
            throw new CadastroIndisponivelException(MENSAGEM_DUPLICADO);
        }

        try {
            authServiceClient.registrarPacienteComAtivacaoPorEmail(email, request.senha(), pacienteId);
        } catch (HttpClientErrorException e) {
            pacienteService.removerAutoCadastroIncompleto(pacienteId);
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new CadastroIndisponivelException(MENSAGEM_DUPLICADO);
            }
            log.warn("Auth recusou o login do auto-cadastro ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalArgumentException("Não foi possível criar o acesso com estes dados. Revise e tente novamente.");
        } catch (RestClientException e) {
            pacienteService.removerAutoCadastroIncompleto(pacienteId);
            log.error("ms-sboot-auth indisponível no auto-cadastro: {}", e.getMessage());
            throw new ServicoIndisponivelException("Cadastro temporariamente indisponível. Tente novamente em instantes.", e);
        }

        log.info("Auto-cadastro de paciente concluído, aguardando ativação por e-mail: id={}", pacienteId);
        return new AutoCadastroPacienteResponse(email, true);
    }

    private void limitarPorIp(String ip) {
        String chave = PREFIXO_IP + (ip == null ? "desconhecido" : ip);
        Long tentativas;
        try {
            tentativas = redis.opsForValue().increment(chave);
            if (tentativas != null && tentativas == 1L) {
                redis.expire(chave, JANELA);
            }
        } catch (DataAccessException e) {
            throw new ServicoIndisponivelException("Cadastro temporariamente indisponível. Tente novamente em instantes.", e);
        }
        if (tentativas != null && tentativas > limitePorIp) {
            throw new MuitasTentativasException("Muitas tentativas de cadastro a partir desta rede. Tente novamente mais tarde.");
        }
    }

    private void validar(AutoCadastroPacienteRequest r) {
        if (r.nome() == null || r.nome().trim().length() < 3) {
            throw new IllegalArgumentException("Informe o nome completo.");
        }
        if (r.email() == null || !EMAIL.matcher(r.email().trim()).matches()) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        if (r.senha() == null || r.senha().length() < TAMANHO_MINIMO_SENHA) {
            throw new IllegalArgumentException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
        LocalDate hoje = LocalDate.now(clock);
        if (r.dataNascimento() == null || r.dataNascimento().isAfter(hoje)
                || r.dataNascimento().isBefore(hoje.minusYears(130))) {
            throw new IllegalArgumentException("Informe uma data de nascimento válida.");
        }
        // CPF (formato e dígitos) e consentimento LGPD são validados em PacienteService.cadastrar
    }
}
