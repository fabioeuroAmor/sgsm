package br.com.sgsm.whatsapp.consumer;

import br.com.sgsm.domain.Agendamento;
import br.com.sgsm.domain.Medico;
import br.com.sgsm.domain.Paciente;
import br.com.sgsm.domain.ServicoMedico;
import br.com.sgsm.repository.AgendamentoRepository;
import br.com.sgsm.repository.MedicoRepository;
import br.com.sgsm.repository.PacienteRepository;
import br.com.sgsm.repository.ServicoMedicoRepository;
import br.com.sgsm.whatsapp.client.WhatsAppService;
import br.com.sgsm.whatsapp.service.ContatoPacienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoConsumerTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private StreamOperations<String, Object, Object> streamOps;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private MedicoRepository medicoRepository;
    @Mock
    private ServicoMedicoRepository servicoMedicoRepository;
    @Mock
    private WhatsAppService whatsAppService;
    @Mock
    private ContatoPacienteService contatoPacienteService;

    private NotificacaoConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificacaoConsumer(redis, pacienteRepository, agendamentoRepository,
                medicoRepository, servicoMedicoRepository, whatsAppService, contatoPacienteService);
        lenient().when(redis.opsForStream()).thenReturn(streamOps);
    }

    private static Map<Object, Object> valores(String tipoNotificacao, String agendamentoId, String pacienteId) {
        Map<Object, Object> valores = new HashMap<>();
        valores.put("tipoNotificacao", tipoNotificacao);
        valores.put("agendamentoId", agendamentoId == null ? "" : agendamentoId);
        valores.put("pacienteId", pacienteId);
        return valores;
    }

    private Paciente paciente(String nome, String telefone, boolean optOut) {
        var p = new Paciente();
        p.setNome(nome);
        p.setTelefone(telefone);
        p.setWhatsappOptOut(optOut);
        return p;
    }

    private Agendamento agendamento(UUID medicoId, UUID servicoId) {
        var a = new Agendamento();
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        a.setMedicoId(medicoId);
        a.setServicoMedicoId(servicoId);
        a.setDataHoraInicio(OffsetDateTime.now().plusHours(2));
        return a;
    }

    @Test
    void naoDeveProcessarQuandoNaoHaMensagens() {
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        consumer.processar();

        verifyNoInteractions(pacienteRepository, whatsAppService);
    }

    @Test
    void deveEnviarConfirmacaoDeAgendamentoEConfirmarMensagem() {
        UUID pacienteId = UUID.randomUUID();
        UUID medicoId = UUID.randomUUID();
        UUID servicoId = UUID.randomUUID();
        var agendamento = agendamento(medicoId, servicoId);

        MapRecord<String, Object, Object> mensagem = MapRecord
                .create("sgsm:events:notificacao", valores("CONFIRMACAO_AGENDAMENTO", agendamento.getId().toString(), pacienteId.toString()))
                .withId(RecordId.of("1-1"));
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(mensagem));

        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente("Ingrid", "5561999998888", false)));
        when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(medicoRepository.findById(medicoId)).thenReturn(Optional.of(medicoComNome("Dra. Ana")));
        when(servicoMedicoRepository.findById(servicoId)).thenReturn(Optional.of(servicoComNome("Cardiologia")));

        consumer.processar();

        verify(whatsAppService).enviarTexto(eq("5561999998888"), contains("confirmada"));
        verify(contatoPacienteService).registrarSaida(eq(pacienteId.toString()), anyString());
        verify(streamOps).acknowledge("sgsm:events:notificacao", "sgsm-notificacao-group", RecordId.of("1-1"));
    }

    @Test
    void naoDeveEnviarQuandoPacienteFezOptOut() {
        UUID pacienteId = UUID.randomUUID();
        MapRecord<String, Object, Object> mensagem = MapRecord
                .create("sgsm:events:notificacao", valores("RECUPERACAO_INATIVO", null, pacienteId.toString()))
                .withId(RecordId.of("1-2"));
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(mensagem));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente("Ana", "5561999998888", true)));

        consumer.processar();

        verifyNoInteractions(whatsAppService);
        verify(streamOps).acknowledge("sgsm:events:notificacao", "sgsm-notificacao-group", RecordId.of("1-2"));
    }

    @Test
    void naoDeveEnviarQuandoPacienteSemTelefone() {
        UUID pacienteId = UUID.randomUUID();
        MapRecord<String, Object, Object> mensagem = MapRecord
                .create("sgsm:events:notificacao", valores("RECUPERACAO_INATIVO", null, pacienteId.toString()))
                .withId(RecordId.of("1-3"));
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(mensagem));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente("Ana", null, false)));

        consumer.processar();

        verifyNoInteractions(whatsAppService);
    }

    @Test
    void deveEnviarMensagemDeRecuperacaoDeInativo() {
        UUID pacienteId = UUID.randomUUID();
        MapRecord<String, Object, Object> mensagem = MapRecord
                .create("sgsm:events:notificacao", valores("RECUPERACAO_INATIVO", null, pacienteId.toString()))
                .withId(RecordId.of("1-4"));
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(mensagem));
        when(pacienteRepository.findById(pacienteId)).thenReturn(Optional.of(paciente("Ana", "5561999998888", false)));

        consumer.processar();

        verify(whatsAppService).enviarTexto(eq("5561999998888"), contains("Sentimos sua falta"));
    }

    @Test
    void naoDeveConfirmarMensagemQuandoProcessamentoFalha() {
        UUID pacienteId = UUID.randomUUID();
        MapRecord<String, Object, Object> mensagem = MapRecord
                .create("sgsm:events:notificacao", valores("RECUPERACAO_INATIVO", null, pacienteId.toString()))
                .withId(RecordId.of("1-5"));
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(mensagem));
        when(pacienteRepository.findById(pacienteId)).thenThrow(new RuntimeException("erro db"));

        consumer.processar();

        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    private Medico medicoComNome(String nome) {
        var m = new Medico();
        m.setNome(nome);
        return m;
    }

    private ServicoMedico servicoComNome(String nome) {
        var s = new ServicoMedico();
        s.setNome(nome);
        return s;
    }
}
