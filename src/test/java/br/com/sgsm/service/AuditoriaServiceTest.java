package br.com.sgsm.service;

import br.com.sgsm.domain.AcaoAuditoria;
import br.com.sgsm.domain.LogAcesso;
import br.com.sgsm.repository.LogAcessoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private LogAcessoRepository repository;
    @Mock
    private ContextoSeguranca contextoSeguranca;

    private AuditoriaService service;

    @BeforeEach
    void setUp() {
        service = new AuditoriaService(repository, contextoSeguranca);
    }

    @Test
    void deveRegistrarLogDeAcessoComDadosDoContextoAtual() {
        UUID usuarioId = UUID.randomUUID();
        UUID entidadeId = UUID.randomUUID();
        when(contextoSeguranca.getReferenciaId()).thenReturn(usuarioId);
        when(contextoSeguranca.getPerfil()).thenReturn("MEDICO");
        when(contextoSeguranca.getEmail()).thenReturn("medico@teste.com");

        service.registrar("PACIENTE", entidadeId, AcaoAuditoria.LEITURA);

        var captor = ArgumentCaptor.forClass(LogAcesso.class);
        verify(repository).save(captor.capture());
        var log = captor.getValue();
        assertThat(log.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(log.getPerfil()).isEqualTo("MEDICO");
        assertThat(log.getEmail()).isEqualTo("medico@teste.com");
        assertThat(log.getEntidade()).isEqualTo("PACIENTE");
        assertThat(log.getEntidadeId()).isEqualTo(entidadeId);
        assertThat(log.getAcao()).isEqualTo(AcaoAuditoria.LEITURA);
    }

    @Test
    void deveRegistrarLogMesmoSemUsuarioIdentificado() {
        UUID entidadeId = UUID.randomUUID();
        when(contextoSeguranca.getReferenciaId()).thenReturn(null);
        when(contextoSeguranca.getPerfil()).thenReturn(null);
        when(contextoSeguranca.getEmail()).thenReturn(null);

        service.registrar("PACIENTE", entidadeId, AcaoAuditoria.EXPORTACAO);

        var captor = ArgumentCaptor.forClass(LogAcesso.class);
        verify(repository).save(captor.capture());
        var log = captor.getValue();
        assertThat(log.getUsuarioId()).isNull();
        assertThat(log.getEntidadeId()).isEqualTo(entidadeId);
        assertThat(log.getAcao()).isEqualTo(AcaoAuditoria.EXPORTACAO);
    }
}
