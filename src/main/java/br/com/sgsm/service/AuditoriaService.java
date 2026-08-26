package br.com.sgsm.service;

import br.com.sgsm.domain.AcaoAuditoria;
import br.com.sgsm.domain.LogAcesso;
import br.com.sgsm.repository.LogAcessoRepository;
import br.com.sgsm.security.ContextoSeguranca;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditoriaService {

    private final LogAcessoRepository repository;
    private final ContextoSeguranca contextoSeguranca;

    public AuditoriaService(LogAcessoRepository repository, ContextoSeguranca contextoSeguranca) {
        this.repository = repository;
        this.contextoSeguranca = contextoSeguranca;
    }

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
