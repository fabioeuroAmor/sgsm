package br.com.sgsm.scheduler;

import br.com.sgsm.service.LgpdExpurgoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** LGPD 3.4 — cron mensal (dia 1, 02:00), conforme POLITICA_RETENCAO_DADOS.md, seção 7. */
@Component
public class LgpdExpurgoScheduler {

    private final LgpdExpurgoService service;

    public LgpdExpurgoScheduler(LgpdExpurgoService service) {
        this.service = service;
    }

    @Scheduled(cron = "${lgpd.expurgo.cron:0 0 2 1 * *}")
    public void auditarRetencaoDados() {
        service.gerarRelatorio();
    }
}
