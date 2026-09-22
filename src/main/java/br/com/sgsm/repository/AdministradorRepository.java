package br.com.sgsm.repository;

import br.com.sgsm.domain.Administrador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdministradorRepository extends JpaRepository<Administrador, UUID> {

    boolean existsByCpf(String cpf);
    boolean existsByCpfAndIdNot(String cpf, UUID id);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, UUID id);

    List<Administrador> findAllByAtivo(Boolean ativo);
}
