package br.com.sgsm.service;

import br.com.sgsm.client.EvolutionInstanciaClient;
import br.com.sgsm.domain.Estabelecimento;
import br.com.sgsm.domain.WhatsappCanal;
import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.exception.ServicoIndisponivelException;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.WhatsappCanalRepository;
import br.com.sgsm.security.ContextoSeguranca;
import br.com.sgsm.security.EstabelecimentoAcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsappCanalServiceTest {

    @Mock private WhatsappCanalRepository repository;
    @Mock private EstabelecimentoRepository estabelecimentoRepository;
    @Mock private EstabelecimentoAcessoService acesso;
    @Mock private ContextoSeguranca contexto;
    @Mock private EvolutionInstanciaClient evolution;
    @Mock private AlertaWhatsappService alerta;

    private WhatsappCanalService service;
    private final UUID clinica = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WhatsappCanalService(repository, estabelecimentoRepository, acesso, contexto, evolution, alerta);
        lenient().when(estabelecimentoRepository.existsById(clinica)).thenReturn(true);
        lenient().when(repository.save(any(WhatsappCanal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void comoAdminDaClinica() {
        when(contexto.isAdminEstabelecimento()).thenReturn(true);
    }

    private WhatsappCanal canal(String status) {
        var c = new WhatsappCanal();
        c.setEstabelecimentoId(clinica);
        c.setInstancia("sgsm-abc-123");
        c.setStatus(status);
        return c;
    }

    @Test
    void adminConectaONumeroDaClinicaERecebeOQrCode() {
        comoAdminDaClinica();
        when(contexto.getUsuarioId()).thenReturn(UUID.randomUUID().toString());
        when(repository.findFirstByEstabelecimentoIdAndStatusNot(clinica, WhatsappCanal.REMOVIDO)).thenReturn(Optional.empty());
        when(evolution.criar(anyString())).thenReturn("data:image/png;base64,QR");

        var resposta = service.conectar(clinica);

        assertThat(resposta.status()).isEqualTo(WhatsappCanal.PENDENTE_QR);
        assertThat(resposta.qrCodeBase64()).isEqualTo("data:image/png;base64,QR");
        var salvo = ArgumentCaptor.forClass(WhatsappCanal.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getInstancia()).startsWith("sgsm-" + clinica.toString().substring(0, 8));
        assertThat(salvo.getValue().getCriadoPor()).isNotNull();
        verify(acesso).validarEstabelecimentoPermitido(clinica);
    }

    @Test
    void clinicaComNumeroJaCriadoReaproveitaOCanalComQrNovo() {
        comoAdminDaClinica();
        when(repository.findFirstByEstabelecimentoIdAndStatusNot(clinica, WhatsappCanal.REMOVIDO))
                .thenReturn(Optional.of(canal(WhatsappCanal.PENDENTE_QR)));
        when(evolution.estado("sgsm-abc-123")).thenReturn("connecting");
        when(evolution.qrCode("sgsm-abc-123")).thenReturn("QR2");

        var resposta = service.conectar(clinica);

        assertThat(resposta.qrCodeBase64()).isEqualTo("QR2");
        verify(evolution, never()).criar(anyString());
    }

    @Test
    void statusConectadoGravaNumeroEDataENaoMandaQr() {
        when(contexto.isFuncionario()).thenReturn(true);
        var c = canal(WhatsappCanal.PENDENTE_QR);
        when(repository.findFirstByEstabelecimentoIdAndStatusNot(clinica, WhatsappCanal.REMOVIDO)).thenReturn(Optional.of(c));
        when(evolution.estado("sgsm-abc-123")).thenReturn("open");
        when(evolution.numeroConectado("sgsm-abc-123")).thenReturn("+5561999990000");

        var resposta = service.consultar(clinica);

        assertThat(resposta.status()).isEqualTo(WhatsappCanal.CONECTADO);
        assertThat(resposta.numero()).isEqualTo("+5561999990000");
        assertThat(resposta.conectadoEm()).isNotNull();
        assertThat(resposta.qrCodeBase64()).isNull();
        verify(evolution, never()).qrCode(anyString());
    }

    @Test
    void clinicaSemNumeroDa404ESemPermissaoDa403() {
        when(contexto.isFuncionario()).thenReturn(true);
        when(repository.findFirstByEstabelecimentoIdAndStatusNot(clinica, WhatsappCanal.REMOVIDO)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.consultar(clinica)).isInstanceOf(RecursoNaoEncontradoException.class);

        // secretária vê o status, mas não conecta nem desconecta (DW2)
        assertThatThrownBy(() -> service.conectar(clinica)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.desconectar(clinica)).isInstanceOf(AcessoNegadoException.class);

        when(contexto.isFuncionario()).thenReturn(false); // ex.: médico
        assertThatThrownBy(() -> service.consultar(clinica)).isInstanceOf(AcessoNegadoException.class);

        UUID inexistente = UUID.randomUUID();
        assertThatThrownBy(() -> service.consultar(inexistente)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void desenvolvedorDesconectaMesmoComFalhaNaEvolution() {
        when(contexto.isDesenvolvedor()).thenReturn(true);
        var c = canal(WhatsappCanal.CONECTADO);
        when(repository.findFirstByEstabelecimentoIdAndStatusNot(clinica, WhatsappCanal.REMOVIDO)).thenReturn(Optional.of(c));
        doThrow(new ResourceAccessException("fora")).when(evolution).remover("sgsm-abc-123");

        service.desconectar(clinica);

        assertThat(c.getStatus()).isEqualTo(WhatsappCanal.REMOVIDO);
        verifyNoInteractions(acesso);
    }

    @Test
    void evolutionForaDoArAoConectarOuConsultarVira503() {
        when(contexto.isDesenvolvedor()).thenReturn(true);
        when(repository.findFirstByEstabelecimentoIdAndStatusNot(clinica, WhatsappCanal.REMOVIDO))
                .thenReturn(Optional.empty(), Optional.of(canal(WhatsappCanal.PENDENTE_QR)));
        when(evolution.criar(anyString())).thenThrow(new ResourceAccessException("fora"));
        when(evolution.estado(anyString())).thenThrow(new ResourceAccessException("fora"));

        assertThatThrownBy(() -> service.conectar(clinica)).isInstanceOf(ServicoIndisponivelException.class);
        assertThatThrownBy(() -> service.consultar(clinica)).isInstanceOf(ServicoIndisponivelException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void botResolveAClinicaPelaInstanciaENaoVeInstanciaRemovida() {
        var c = canal(WhatsappCanal.CONECTADO);
        var e = new Estabelecimento();
        e.setNome("Clinica São Lucas");
        when(repository.findByInstancia("sgsm-abc-123")).thenReturn(Optional.of(c));
        when(estabelecimentoRepository.findById(clinica)).thenReturn(Optional.of(e));

        var interno = service.resolver("sgsm-abc-123");

        assertThat(interno.estabelecimentoId()).isEqualTo(clinica);
        assertThat(interno.estabelecimentoNome()).isEqualTo("Clinica São Lucas");

        c.setStatus(WhatsappCanal.REMOVIDO);
        assertThatThrownBy(() -> service.resolver("sgsm-abc-123")).isInstanceOf(RecursoNaoEncontradoException.class);
        when(repository.findByInstancia("outra")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolver("outra")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listaOsConectados() {
        when(repository.findAllByStatus(WhatsappCanal.CONECTADO)).thenReturn(List.of(canal(WhatsappCanal.CONECTADO)));
        when(estabelecimentoRepository.findById(clinica)).thenReturn(Optional.empty());

        assertThat(service.listarConectados()).hasSize(1).first()
                .satisfies(i -> assertThat(i.estabelecimentoNome()).isNull());
    }

    @Test
    void monitorMarcaDesconectadoQuandoONumeroCaiEIgnoraFalhaDeConsulta() {
        var caiu = canal(WhatsappCanal.CONECTADO);
        var outro = canal(WhatsappCanal.PENDENTE_QR);
        outro.setInstancia("sgsm-xyz");
        var semResposta = canal(WhatsappCanal.CONECTADO);
        semResposta.setInstancia("sgsm-fora");
        when(repository.findAllByStatusNot(WhatsappCanal.REMOVIDO)).thenReturn(List.of(caiu, outro, semResposta));
        when(evolution.estado("sgsm-abc-123")).thenReturn("close");
        when(evolution.estado("sgsm-xyz")).thenReturn("close");
        when(evolution.estado("sgsm-fora")).thenThrow(new ResourceAccessException("fora"));

        service.monitorar();

        assertThat(caiu.getStatus()).isEqualTo(WhatsappCanal.DESCONECTADO);
        assertThat(outro.getStatus()).isEqualTo(WhatsappCanal.PENDENTE_QR);
        assertThat(semResposta.getStatus()).isEqualTo(WhatsappCanal.CONECTADO);
        verify(repository, times(1)).save(caiu);
        // só quem caiu agora gera alerta (uma vez, na transição)
        verify(alerta).numeroDesconectado(caiu);
        verifyNoMoreInteractions(alerta);
    }

    @Test
    void nomeDaInstanciaIdentificaAClinicaENaoRepete() {
        assertThat(WhatsappCanalService.nomeDaInstancia(clinica))
                .startsWith("sgsm-" + clinica.toString().substring(0, 8) + "-")
                .isNotEqualTo(WhatsappCanalService.nomeDaInstancia(clinica));
    }
}
