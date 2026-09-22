package br.com.sgsm.repository;

import br.com.sgsm.domain.MedicoEstabelecimento;
import br.com.sgsm.domain.MedicoEstabelecimentoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MedicoEstabelecimentoRepository
        extends JpaRepository<MedicoEstabelecimento, MedicoEstabelecimentoId> {

    List<MedicoEstabelecimento> findById_EstabelecimentoIdAndAtivo(UUID estabelecimentoId, Boolean ativo);

    List<MedicoEstabelecimento> findById_MedicoIdAndAtivo(UUID medicoId, Boolean ativo);

    List<MedicoEstabelecimento> findById_EstabelecimentoIdInAndAtivo(List<UUID> estabelecimentoIds, Boolean ativo);

    @Query("SELECT COUNT(me) > 0 FROM MedicoEstabelecimento me " +
           "WHERE me.id.medicoId = :medicoId AND me.id.estabelecimentoId IN :estabelecimentoIds AND me.ativo = true")
    boolean existsVinculoAtivo(@Param("medicoId") UUID medicoId, @Param("estabelecimentoIds") List<UUID> estabelecimentoIds);

    @Modifying
    @Query("DELETE FROM MedicoEstabelecimento me WHERE me.id.estabelecimentoId = :estabelecimentoId")
    void deleteByEstabelecimentoId(@Param("estabelecimentoId") UUID estabelecimentoId);
}
