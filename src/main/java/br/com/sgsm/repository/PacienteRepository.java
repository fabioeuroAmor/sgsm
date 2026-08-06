package br.com.sgsm.repository;

import br.com.sgsm.domain.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PacienteRepository extends JpaRepository<Paciente, UUID> {

    List<Paciente> findAllByAtivo(Boolean ativo);

    List<Paciente> findAllByNomeContainingIgnoreCase(String nome);

    boolean existsByCpf(String cpf);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    boolean existsByTelefone(String telefone);

    boolean existsByTelefoneAndIdNot(String telefone, UUID id);
}
