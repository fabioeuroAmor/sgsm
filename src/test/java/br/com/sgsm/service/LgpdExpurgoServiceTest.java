package br.com.sgsm.service;

import br.com.sgsm.domain.Paciente;
import br.com.sgsm.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LgpdExpurgoServiceTest {

    @Mock
    private PacienteRepository repository;

    private LgpdExpurgoService service;

    @BeforeEach
    void setUp() {
        service = new LgpdExpurgoService(repository, 20);
    }

    private Paciente novoPaciente() {
        var p = new Paciente();
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        return p;
    }

    @Test
    void deveListarElegiveisUsandoLimiteDeRetencaoConfigurado() {
        when(repository.findAllByAtivoFalseAndAnonimizadoFalseAndEncerradoEmBefore(any()))
                .thenReturn(List.of(novoPaciente()));

        var resultado = service.listarElegiveis();

        assertThat(resultado).hasSize(1);
        var captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).findAllByAtivoFalseAndAnonimizadoFalseAndEncerradoEmBefore(captor.capture());
        // Limite deve ser ~20 anos atrás (tolerância de alguns segundos pela execução do teste)
        var esperado = OffsetDateTime.now().minusYears(20);
        assertThat(captor.getValue()).isCloseTo(esperado, within(5, ChronoUnit.SECONDS));
    }

    @Test
    void naoDeveAnonimizarNemSalvarAoGerarRelatorio() {
        when(repository.findAllByAtivoFalseAndAnonimizadoFalseAndEncerradoEmBefore(any()))
                .thenReturn(List.of(novoPaciente(), novoPaciente()));

        service.gerarRelatorio();

        // Job só relata — nunca chama save()/anonimizar() (aprovação manual, ver POLITICA_RETENCAO_DADOS.md)
        verify(repository, never()).save(any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void naoDeveLancarExcecaoQuandoNenhumElegivel() {
        when(repository.findAllByAtivoFalseAndAnonimizadoFalseAndEncerradoEmBefore(any()))
                .thenReturn(List.of());

        service.gerarRelatorio();

        verify(repository, never()).save(any());
    }
}
