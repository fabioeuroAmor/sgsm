package br.com.sgsm.repository;

import br.com.sgsm.domain.ConsentimentoLgpd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentimentoLgpdRepository extends JpaRepository<ConsentimentoLgpd, UUID> {

    boolean existsByTelefoneAndEstabelecimentoIdAndCanalAndRevogadoEmIsNull(String telefone, UUID estabelecimentoId, String canal);

    List<ConsentimentoLgpd> findAllByTelefoneAndEstabelecimentoIdAndRevogadoEmIsNull(String telefone, UUID estabelecimentoId);

    List<ConsentimentoLgpd> findAllByPacienteId(UUID pacienteId);
}
