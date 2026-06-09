package br.com.sgsm.repository;

import br.com.sgsm.domain.AgendaMedico;
import br.com.sgsm.domain.DiaSemana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AgendaMedicoRepository extends JpaRepository<AgendaMedico, UUID> {

    List<AgendaMedico> findByMedicoIdAndEstabelecimentoIdAndDiaSemanaAndAtivo(
            UUID medicoId, UUID estabelecimentoId, DiaSemana diaSemana, Boolean ativo);

    List<AgendaMedico> findByMedicoIdAndDomiciliarTrueAndDiaSemanaAndAtivo(
            UUID medicoId, DiaSemana diaSemana, Boolean ativo);

    List<AgendaMedico> findByMedicoIdOrderByDiaSemanaAscHoraInicioAsc(UUID medicoId);

    @Query("SELECT DISTINCT a.estabelecimentoId FROM AgendaMedico a " +
           "WHERE a.medicoId = :medicoId AND a.ativo = true")
    List<UUID> findEstabelecimentoIdsByMedicoId(@Param("medicoId") UUID medicoId);
}
