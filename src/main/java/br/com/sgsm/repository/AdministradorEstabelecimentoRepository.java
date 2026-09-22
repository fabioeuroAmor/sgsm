package br.com.sgsm.repository;

import br.com.sgsm.domain.AdministradorEstabelecimento;
import br.com.sgsm.domain.AdministradorEstabelecimentoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AdministradorEstabelecimentoRepository
        extends JpaRepository<AdministradorEstabelecimento, AdministradorEstabelecimentoId> {

    List<AdministradorEstabelecimento> findById_EstabelecimentoIdAndAtivo(UUID estabelecimentoId, Boolean ativo);

    List<AdministradorEstabelecimento> findById_AdministradorIdAndAtivo(UUID administradorId, Boolean ativo);

    @Modifying
    @Query("DELETE FROM AdministradorEstabelecimento ae WHERE ae.id.estabelecimentoId = :estabelecimentoId")
    void deleteByEstabelecimentoId(@Param("estabelecimentoId") UUID estabelecimentoId);

    @Modifying
    @Query("DELETE FROM AdministradorEstabelecimento ae WHERE ae.id.administradorId = :administradorId")
    void deleteByAdministradorId(@Param("administradorId") UUID administradorId);
}
