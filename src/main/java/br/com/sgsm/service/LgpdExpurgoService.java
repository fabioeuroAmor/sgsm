package br.com.sgsm.service;

import br.com.sgsm.domain.Paciente;
import br.com.sgsm.repository.PacienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * LGPD 3.4 — auditoria mensal de retenção (POLITICA_RETENCAO_DADOS.md, seção 7):
 * identifica pacientes encerrados há mais tempo que o prazo legal (20 anos) e
 * GERA UM RELATÓRIO, sem anonimizar automaticamente — a anonimização em si é uma
 * chamada manual ao endpoint PATCH /{id}/anonimizar, em lote, após aprovação
 * de um administrador.
 */
@Service
public class LgpdExpurgoService {

    private static final Logger log = LoggerFactory.getLogger(LgpdExpurgoService.class);

    private final PacienteRepository repository;
    private final int anosRetencao;

    public LgpdExpurgoService(PacienteRepository repository,
                              @Value("${lgpd.expurgo.anos-retencao:20}") int anosRetencao) {
        this.repository = repository;
        this.anosRetencao = anosRetencao;
    }

    @Transactional(readOnly = true)
    public List<Paciente> listarElegiveis() {
        OffsetDateTime limite = OffsetDateTime.now().minusYears(anosRetencao);
        return repository.findAllByAtivoFalseAndAnonimizadoFalseAndEncerradoEmBefore(limite);
    }

    public void gerarRelatorio() {
        var elegiveis = listarElegiveis();
        if (elegiveis.isEmpty()) {
            log.info("Auditoria de retenção LGPD: nenhum paciente elegível para anonimização (retenção de {} anos).", anosRetencao);
            return;
        }
        log.info("Auditoria de retenção LGPD: {} paciente(s) encerrado(s) há mais de {} anos, elegível(is) para anonimização "
                + "(aguardando aprovação manual via PATCH /v1/api/pacientes/{{id}}/anonimizar): {}",
                elegiveis.size(), anosRetencao,
                elegiveis.stream().map(Paciente::getId).toList());
    }
}
