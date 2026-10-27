package br.com.sgsm.service;

import br.com.sgsm.domain.ConsentimentoLgpd;
import br.com.sgsm.domain.PacienteEstabelecimento;
import br.com.sgsm.dto.ConsentimentoRequest;
import br.com.sgsm.dto.VinculoPacienteClinicaRequest;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.ConsentimentoLgpdRepository;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.PacienteEstabelecimentoRepository;
import br.com.sgsm.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacienteClinicaServiceTest {

    @Mock private PacienteEstabelecimentoRepository vinculoRepository;
    @Mock private ConsentimentoLgpdRepository consentimentoRepository;
    @Mock private PacienteRepository pacienteRepository;
    @Mock private EstabelecimentoRepository estabelecimentoRepository;

    private PacienteClinicaService service;
    private final UUID paciente = UUID.randomUUID();
    private final UUID clinica = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PacienteClinicaService(vinculoRepository, consentimentoRepository, pacienteRepository,
                estabelecimentoRepository);
        lenient().when(pacienteRepository.existsById(paciente)).thenReturn(true);
        lenient().when(estabelecimentoRepository.existsById(clinica)).thenReturn(true);
    }

    @Test
    void vinculoPeloWhatsAppEhIdempotenteEAssociaOConsentimentoDadoAntesDoCadastro() {
        when(vinculoRepository.existsById(any())).thenReturn(false, true);
        var dadoAntes = new ConsentimentoLgpd();
        dadoAntes.setTelefone("5561999990000");
        when(consentimentoRepository.findAllByTelefoneAndEstabelecimentoIdAndRevogadoEmIsNull("5561999990000", clinica))
                .thenReturn(List.of(dadoAntes));

        service.vincular(new VinculoPacienteClinicaRequest(paciente, clinica, "whatsapp", "+55 (61) 99999-0000"));
        service.vincular(new VinculoPacienteClinicaRequest(paciente, clinica, "WHATSAPP", null));

        var salvo = ArgumentCaptor.forClass(PacienteEstabelecimento.class);
        verify(vinculoRepository, times(1)).save(salvo.capture());
        assertThat(salvo.getValue().getOrigem()).isEqualTo("WHATSAPP");
        assertThat(dadoAntes.getPacienteId()).isEqualTo(paciente);
        verify(consentimentoRepository).save(dadoAntes);
    }

    @Test
    void vinculoValidaDados() {
        assertThatThrownBy(() -> service.vincular(new VinculoPacienteClinicaRequest(null, clinica, "WHATSAPP", null)))
                .hasMessageContaining("Informe o paciente");
        assertThatThrownBy(() -> service.vincular(new VinculoPacienteClinicaRequest(paciente, clinica, "EMAIL", null)))
                .hasMessageContaining("Origem inválida");
        UUID outro = UUID.randomUUID();
        assertThatThrownBy(() -> service.vincular(new VinculoPacienteClinicaRequest(outro, clinica, "SITE", null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        UUID outraClinica = UUID.randomUUID();
        assertThatThrownBy(() -> service.vincular(new VinculoPacienteClinicaRequest(paciente, outraClinica, "SITE", null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(vinculoRepository, never()).save(any());
    }

    @Test
    void cadastroPelaEquipeVinculaAsClinicasQueAindaNaoTemVinculo() {
        UUID outraClinica = UUID.randomUUID();
        when(vinculoRepository.existsById(any())).thenReturn(true, false);

        service.vincularAsClinicas(paciente, List.of(clinica, outraClinica));

        var salvo = ArgumentCaptor.forClass(PacienteEstabelecimento.class);
        verify(vinculoRepository, times(1)).save(salvo.capture());
        assertThat(salvo.getValue().getId().getEstabelecimentoId()).isEqualTo(outraClinica);
        assertThat(salvo.getValue().getOrigem()).isEqualTo("CLINICA");
    }

    @Test
    void pacientesDasClinicas() {
        when(vinculoRepository.findPacienteIdsByEstabelecimentoIdIn(List.of(clinica))).thenReturn(List.of(paciente));

        assertThat(service.pacientesDasClinicas(List.of(clinica))).containsExactly(paciente);
        assertThat(service.pacientesDasClinicas(List.of())).isEmpty();
    }

    @Test
    void consentimentoPorClinicaECanal() {
        service.registrarConsentimento(new ConsentimentoRequest("+55 61 99999-0000", null, clinica, "whatsapp", "lgpd-v1"));

        var salvo = ArgumentCaptor.forClass(ConsentimentoLgpd.class);
        verify(consentimentoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getTelefone()).isEqualTo("5561999990000");
        assertThat(salvo.getValue().getCanal()).isEqualTo("WHATSAPP");
        assertThat(salvo.getValue().getVersaoTexto()).isEqualTo("lgpd-v1");

        when(consentimentoRepository.existsByTelefoneAndEstabelecimentoIdAndCanalAndRevogadoEmIsNull("5561999990000", clinica, "WHATSAPP"))
                .thenReturn(true);
        assertThat(service.consentimentoVigente("+5561999990000", clinica, "whatsapp")).isTrue();
        assertThat(service.consentimentoVigente(null, clinica, "WHATSAPP")).isFalse();
    }

    @Test
    void consentimentoValidaDados() {
        assertThatThrownBy(() -> service.registrarConsentimento(new ConsentimentoRequest("1", null, clinica, "FAX", "v1")))
                .hasMessageContaining("Canal inválido");
        UUID outra = UUID.randomUUID();
        assertThatThrownBy(() -> service.registrarConsentimento(new ConsentimentoRequest("1", null, outra, "SITE", "v1")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.registrarConsentimento(new ConsentimentoRequest(" ", null, clinica, "SITE", "v1")))
                .hasMessageContaining("telefone ou o paciente");
        assertThatThrownBy(() -> service.registrarConsentimento(new ConsentimentoRequest("1", null, clinica, "SITE", " ")))
                .hasMessageContaining("versão");
        verify(consentimentoRepository, never()).save(any());
    }

    @Test
    void dadosDoTitularParaExportacao() {
        when(consentimentoRepository.findAllByPacienteId(paciente)).thenReturn(List.of(new ConsentimentoLgpd()));
        when(vinculoRepository.findAllById_PacienteId(paciente))
                .thenReturn(List.of(new PacienteEstabelecimento(paciente, clinica, "SITE")));

        assertThat(service.consentimentosDoPaciente(paciente)).hasSize(1);
        assertThat(service.vinculosDoPaciente(paciente)).hasSize(1);
    }
}
