package br.com.sgsm.service;

import br.com.sgsm.client.AuthServiceClient;
import br.com.sgsm.dto.ConviteAcessoMedicoResponse;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.exception.ServicoIndisponivelException;
import br.com.sgsm.repository.MedicoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

// Convite de acesso do médico cadastrado pela clínica: o ms-sboot-auth cria o login (sem senha
// conhecida) e manda ao e-mail do cadastro um link para o médico criar a própria senha. O escopo
// fica aqui porque só o sgsm sabe de que clínica é o médico: FUNCIONARIO/ADMIN_ESTABELECIMENTO
// convidam só médicos vinculados às suas clínicas (o médico que cadastram já nasce vinculado — ver
// MedicoService.cadastrar); DESENVOLVEDOR, qualquer um.
@Service
public class ConviteAcessoMedicoService {

    private static final Logger log = LoggerFactory.getLogger(ConviteAcessoMedicoService.class);

    private final MedicoRepository medicoRepository;
    private final EstabelecimentoAcessoService estabelecimentoAcessoService;
    private final ContextoSeguranca contextoSeguranca;
    private final AuthServiceClient authServiceClient;

    public ConviteAcessoMedicoService(MedicoRepository medicoRepository,
                                      EstabelecimentoAcessoService estabelecimentoAcessoService,
                                      ContextoSeguranca contextoSeguranca,
                                      AuthServiceClient authServiceClient) {
        this.medicoRepository = medicoRepository;
        this.estabelecimentoAcessoService = estabelecimentoAcessoService;
        this.contextoSeguranca = contextoSeguranca;
        this.authServiceClient = authServiceClient;
    }

    public ConviteAcessoMedicoResponse convidar(UUID medicoId, String authorization) {
        var medico = medicoRepository.findById(medicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Médico não encontrado: " + medicoId));
        if (!Boolean.TRUE.equals(medico.getAtivo())) {
            throw new IllegalArgumentException("Médico inativo: reative o cadastro antes de enviar o convite.");
        }
        if (!podeConvidar(medicoId)) {
            // mesmo 404 de "não existe": não revela médico de outra clínica
            throw new RecursoNaoEncontradoException("Médico não encontrado: " + medicoId);
        }

        try {
            var resposta = authServiceClient.enviarConviteMedico(medicoId, authorization);
            log.info("Convite de acesso enviado ao médico {}", medicoId);
            return resposta;
        } catch (HttpClientErrorException e) {
            log.warn("Auth recusou o convite do médico {} ({}): {}", medicoId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalArgumentException(mensagemDoAuth(e));
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("Não foi possível enviar o convite agora. Tente novamente em instantes.", e);
        }
    }

    private boolean podeConvidar(UUID medicoId) {
        if (!contextoSeguranca.isFuncionario() && !contextoSeguranca.isAdminEstabelecimento()) {
            return true; // DESENVOLVEDOR (o SecurityConfig já barra os demais papéis em POST /medicos/**)
        }
        return estabelecimentoAcessoService.medicoPermitido(medicoId);
    }

    // detail do ProblemDetail do auth (ex.: e-mail do médico já usado por outro acesso)
    private static String mensagemDoAuth(HttpClientErrorException e) {
        String corpo = e.getResponseBodyAsString();
        var m = java.util.regex.Pattern.compile("\"detail\"\\s*:\\s*\"([^\"]*)\"").matcher(corpo == null ? "" : corpo);
        return m.find() ? m.group(1) : "Não foi possível enviar o convite de acesso.";
    }
}
