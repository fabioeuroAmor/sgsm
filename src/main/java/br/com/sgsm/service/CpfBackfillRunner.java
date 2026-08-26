package br.com.sgsm.service;

import br.com.sgsm.repository.PacienteRepository;
import br.com.sgsm.security.CpfCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Migra, uma única vez, os pacientes cadastrados antes da criptografia de CPF
 * (item 2 do plano de compliance): calcula o cpfHash que faltava. O simples ato de
 * salvar a entidade já regrava o CPF cifrado (o converter roda em toda escrita),
 * completando a migração sem precisar de UPDATE manual em massa no banco.
 * Idempotente: só processa linhas com cpfHash nulo, então rodar de novo não faz nada.
 *
 * Cada paciente é salvo em sua própria transação (repository.save() é transacional
 * por linha): dado legado com CPF duplicado (que o índice único cpf_hash agora
 * rejeitaria) não derruba o startup nem impede a migração dos demais — a linha
 * duplicada fica com cpfHash nulo (permitido, o índice único ignora NULL) e é
 * logada para investigação manual, sendo reprocessada nas próximas subidas.
 */
@Component
public class CpfBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CpfBackfillRunner.class);

    private final PacienteRepository repository;
    private final CpfCryptoService cpfCryptoService;

    public CpfBackfillRunner(PacienteRepository repository, CpfCryptoService cpfCryptoService) {
        this.repository = repository;
        this.cpfCryptoService = cpfCryptoService;
    }

    @Override
    public void run(String... args) {
        var pendentes = repository.findAllByCpfHashIsNull();
        if (pendentes.isEmpty()) {
            return;
        }
        int migrados = 0;
        int duplicados = 0;
        for (var paciente : pendentes) {
            String cpfNormalizado = paciente.getCpf() == null ? null : paciente.getCpf().replaceAll("\\D", "");
            String hash = cpfCryptoService.hash(cpfNormalizado);
            if (repository.existsByCpfHash(hash)) {
                log.warn("Backfill de criptografia de CPF: paciente {} tem o mesmo CPF de outro paciente já "
                        + "migrado (hash duplicado) — cpfHash não definido, requer investigação manual de "
                        + "cadastro duplicado legado", paciente.getId());
                duplicados++;
                continue;
            }
            try {
                paciente.setCpfHash(hash);
                repository.save(paciente);
                migrados++;
            } catch (Exception ex) {
                log.warn("Backfill de criptografia de CPF: falha ao migrar paciente {}, será reprocessado na "
                        + "próxima subida", paciente.getId(), ex);
            }
        }
        log.info("Backfill de criptografia de CPF: {} paciente(s) migrado(s), {} pendente(s) por CPF duplicado legado",
                migrados, duplicados);
    }
}
