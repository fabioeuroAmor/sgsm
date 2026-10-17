package br.com.sgsm.repository;

import br.com.sgsm.domain.Agendamento;
import br.com.sgsm.domain.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {

    List<Agendamento> findAllByPacienteId(UUID pacienteId);

    List<Agendamento> findAllByMedicoId(UUID medicoId);

    List<Agendamento> findAllByStatus(StatusAgendamento status);

    List<Agendamento> findAllByPacienteIdAndStatus(UUID pacienteId, StatusAgendamento status);

    List<Agendamento> findAllByMedicoIdAndStatus(UUID medicoId, StatusAgendamento status);

    List<Agendamento> findAllByMedicoIdAndPacienteId(UUID medicoId, UUID pacienteId);

    List<Agendamento> findAllByMedicoIdAndPacienteIdAndStatus(UUID medicoId, UUID pacienteId, StatusAgendamento status);

    // Sobreposição com o período (não só "começa no dia"): pega também o atendimento que começa
    // antes e termina dentro dele, como um que atravessa a meia-noite
    @Query("SELECT a FROM Agendamento a " +
           "WHERE a.medicoId = :medicoId " +
           "AND a.dataHoraInicio < :fimDia " +
           "AND a.dataHoraFim > :inicioDia " +
           "AND a.status NOT IN :statusExcluidos")
    List<Agendamento> findOcupadosByMedicoAndData(
            @Param("medicoId") UUID medicoId,
            @Param("inicioDia") OffsetDateTime inicioDia,
            @Param("fimDia") OffsetDateTime fimDia,
            @Param("statusExcluidos") Collection<StatusAgendamento> statusExcluidos);

    // Visibilidade de paciente para FUNCIONARIO/ADMIN_ESTABELECIMENTO: por médico
    // vinculado ao estabelecimento, não por Agendamento.estabelecimentoId (que fica
    // nulo em agendamento domiciliar) — inclui paciente atendido só em domicílio.
    @Query("SELECT DISTINCT a.pacienteId FROM Agendamento a WHERE a.medicoId IN :medicoIds")
    List<UUID> findPacienteIdsDistintosByMedicoIdIn(@Param("medicoIds") Collection<UUID> medicoIds);
}
