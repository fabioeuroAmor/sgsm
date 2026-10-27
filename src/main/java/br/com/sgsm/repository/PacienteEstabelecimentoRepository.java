package br.com.sgsm.repository;

import br.com.sgsm.domain.PacienteEstabelecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PacienteEstabelecimentoRepository
        extends JpaRepository<PacienteEstabelecimento, PacienteEstabelecimento.Id> {

    @Query("SELECT DISTINCT pe.id.pacienteId FROM PacienteEstabelecimento pe WHERE pe.id.estabelecimentoId IN :estabelecimentos")
    List<UUID> findPacienteIdsByEstabelecimentoIdIn(@Param("estabelecimentos") Collection<UUID> estabelecimentos);

    List<PacienteEstabelecimento> findAllById_PacienteId(UUID pacienteId);
}
