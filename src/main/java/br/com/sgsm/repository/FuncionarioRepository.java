package br.com.sgsm.repository;

import br.com.sgsm.domain.Funcionario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FuncionarioRepository extends JpaRepository<Funcionario, UUID> {

    boolean existsByCpf(String cpf);
    boolean existsByCpfAndIdNot(String cpf, UUID id);
    boolean existsByEmailAndIdNot(String email, UUID id);
    boolean existsByTelefone(String telefone);
    boolean existsByTelefoneAndIdNot(String telefone, UUID id);

    List<Funcionario> findAllByEstabelecimentoId(UUID estabelecimentoId);
    List<Funcionario> findAllByEstabelecimentoIdAndAtivo(UUID estabelecimentoId, Boolean ativo);
    List<Funcionario> findAllByEstabelecimentoIdIn(List<UUID> estabelecimentoIds);
    List<Funcionario> findAllByEstabelecimentoIdInAndAtivo(List<UUID> estabelecimentoIds, Boolean ativo);
}
