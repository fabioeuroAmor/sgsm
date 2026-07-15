package br.com.sgsm.service;

import br.com.sgsm.domain.ServicoMedico;
import br.com.sgsm.dto.AtualizarServicoMedicoRequest;
import br.com.sgsm.dto.CadastrarServicoMedicoRequest;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.ServicoMedicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicoMedicoServiceTest {

    @Mock
    private ServicoMedicoRepository repository;

    private ServicoMedicoService service;

    @BeforeEach
    void setUp() {
        service = new ServicoMedicoService(repository, new br.com.sgsm.config.ModelMapperConfig().modelMapper());
    }

    private ServicoMedico novoServico() {
        var s = new ServicoMedico();
        s.setMedicoId(UUID.randomUUID());
        s.setNome("Consulta cardiológica");
        s.setPreco(new BigDecimal("250.00"));
        s.setDuracaoMinutos(30);
        s.setDomiciliar(false);
        s.setAtivo(true);
        return s;
    }

    @Test
    void deveCadastrarServicoMedico() {
        var request = new CadastrarServicoMedicoRequest(
                UUID.randomUUID(), "Consulta cardiológica", "Avaliação clínica",
                new BigDecimal("250.00"), 30, false, null);
        when(repository.save(any(ServicoMedico.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.cadastrar(request);

        assertThat(response.getNome()).isEqualTo("Consulta cardiológica");
        assertThat(response.getPreco()).isEqualByComparingTo("250.00");
        verify(repository).save(any(ServicoMedico.class));
    }

    @Test
    void deveConsultarServicoMedicoExistente() {
        var servico = novoServico();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(servico));

        var response = service.consultar(id);

        assertThat(response.getNome()).isEqualTo(servico.getNome());
    }

    @Test
    void deveLancarExcecaoAoConsultarServicoMedicoInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(id))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void deveAtualizarServicoMedicoExistente() {
        var servico = novoServico();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(servico));
        when(repository.save(any(ServicoMedico.class))).thenAnswer(inv -> inv.getArgument(0));
        var request = new AtualizarServicoMedicoRequest(
                "Consulta cardiológica premium", null, new BigDecimal("300.00"), null, null, null);

        var response = service.atualizar(id, request);

        assertThat(response.getNome()).isEqualTo("Consulta cardiológica premium");
        assertThat(response.getPreco()).isEqualByComparingTo("300.00");
    }

    @Test
    void deveLancarExcecaoAoAtualizarServicoMedicoInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        var request = new AtualizarServicoMedicoRequest("x", null, null, null, null, null);

        assertThatThrownBy(() -> service.atualizar(id, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveInativarServicoMedicoAoRemover() {
        var servico = novoServico();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(servico));

        service.remover(id);

        assertThat(servico.getAtivo()).isFalse();
        verify(repository).save(servico);
    }

    @Test
    void deveLancarExcecaoAoRemoverServicoMedicoInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(id))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void deveListarPorMedicoEAtivoQuandoAmbosInformados() {
        UUID medicoId = UUID.randomUUID();
        when(repository.findAllByMedicoIdAndAtivo(medicoId, true)).thenReturn(List.of(novoServico()));

        var resultado = service.listar(true, medicoId);

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByMedicoIdAndAtivo(medicoId, true);
    }

    @Test
    void deveListarPorMedicoQuandoApenasMedicoInformado() {
        UUID medicoId = UUID.randomUUID();
        when(repository.findAllByMedicoId(medicoId)).thenReturn(List.of(novoServico()));

        var resultado = service.listar(null, medicoId);

        assertThat(resultado).hasSize(1);
        verify(repository).findAllByMedicoId(medicoId);
    }

    @Test
    void deveListarPorAtivoQuandoApenasAtivoInformado() {
        when(repository.findAllByAtivo(false)).thenReturn(List.of());

        var resultado = service.listar(false, null);

        assertThat(resultado).isEmpty();
        verify(repository).findAllByAtivo(false);
    }

    @Test
    void deveListarTodosQuandoNenhumFiltroInformado() {
        when(repository.findAll()).thenReturn(List.of(novoServico(), novoServico()));

        var resultado = service.listar(null, null);

        assertThat(resultado).hasSize(2);
        verify(repository).findAll();
    }
}
