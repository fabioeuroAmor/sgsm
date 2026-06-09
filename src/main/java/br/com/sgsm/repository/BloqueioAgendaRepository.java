package br.com.sgsm.repository;

import br.com.sgsm.domain.BloqueioAgenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BloqueioAgendaRepository extends JpaRepository<BloqueioAgenda, UUID> {

    @Query("SELECT b FROM BloqueioAgenda b " +
           "WHERE b.medicoId = :medicoId " +
           "AND b.dataHoraInicio < :fimDia " +
           "AND b.dataHoraFim > :inicioDia " +
           "AND (b.estabelecimentoId IS NULL OR b.estabelecimentoId = :estabelecimentoId)")
    List<BloqueioAgenda> findByMedicoAndPeriodo(
            @Param("medicoId") UUID medicoId,
            @Param("estabelecimentoId") UUID estabelecimentoId,
            @Param("inicioDia") OffsetDateTime inicioDia,
            @Param("fimDia") OffsetDateTime fimDia);
}
