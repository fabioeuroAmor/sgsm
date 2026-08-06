package br.com.sgsm.repository;

import br.com.sgsm.domain.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MedicoRepository extends JpaRepository<Medico, UUID> {

    List<Medico> findAllByAtivo(Boolean ativo);

    List<Medico> findAllByEspecialidade(String especialidade);

    List<Medico> findAllByEspecialidadeAndAtivo(String especialidade, Boolean ativo);

    boolean existsByCrmAndCrmUf(String crm, String crmUf);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByTelefone(String telefone);

    boolean existsByTelefoneAndIdNot(String telefone, UUID id);
}
