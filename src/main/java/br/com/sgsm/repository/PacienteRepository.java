package br.com.sgsm.repository;

import br.com.sgsm.domain.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PacienteRepository extends JpaRepository<Paciente, UUID> {

    List<Paciente> findAllByAtivo(Boolean ativo);

    boolean existsByCpfHash(String cpfHash);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    List<Paciente> findAllByCpfHashIsNull();

    // LGPD 3.4 — candidatos ao expurgo: encerrados há mais tempo que a retenção legal,
    // ainda não anonimizados (evita reprocessar quem já foi anonimizado).
    List<Paciente> findAllByAtivoFalseAndAnonimizadoFalseAndEncerradoEmBefore(OffsetDateTime limite);
}
