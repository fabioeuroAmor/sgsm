package br.com.sgsm.repository;

import br.com.sgsm.domain.ServicoMedico;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ServicoMedicoRepository extends JpaRepository<ServicoMedico, UUID> {

    List<ServicoMedico> findAllByAtivo(Boolean ativo);

    List<ServicoMedico> findAllByMedicoId(UUID medicoId);

    List<ServicoMedico> findAllByMedicoIdAndAtivo(UUID medicoId, Boolean ativo);
}
