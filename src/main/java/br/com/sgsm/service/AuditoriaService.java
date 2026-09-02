package br.com.sgsm.service;

import br.com.sgsm.domain.AcaoAuditoria;
import br.com.sgsm.domain.LogAcesso;
import br.com.sgsm.repository.LogAcessoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditoriaService {

    private final LogAcessoRepository repository;
    private final ContextoSeguranca contextoSeguranca;

    public AuditoriaService(LogAcessoRepository repository, ContextoSeguranca contextoSeguranca) {
        this.repository = repository;
        this.contextoSeguranca = contextoSeguranca;
    }

    // Transação própria: uma falha ao gravar auditoria (ex.: banco indisponível) não pode
    // reverter a operação de negócio que já foi concluída — ver PacienteService, que chama
    // isto por último e engole a exceção.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String entidade, UUID entidadeId, AcaoAuditoria acao) {
        var log = new LogAcesso();
        log.setUsuarioId(contextoSeguranca.getReferenciaId());
        log.setPerfil(contextoSeguranca.getPerfil());
        log.setEmail(contextoSeguranca.getEmail());
        log.setEntidade(entidade);
        log.setEntidadeId(entidadeId);
        log.setAcao(acao);
        repository.save(log);
    }
}
