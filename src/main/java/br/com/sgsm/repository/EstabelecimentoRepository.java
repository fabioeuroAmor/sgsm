package br.com.sgsm.repository;

import br.com.sgsm.domain.Estabelecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, UUID> {

    List<Estabelecimento> findAllByAtivo(Boolean ativo);

    List<Estabelecimento> findAllByUf(String uf);

    List<Estabelecimento> findAllByUfAndAtivo(String uf, Boolean ativo);

    List<Estabelecimento> findAllByCidade(String cidade);

    List<Estabelecimento> findAllByCidadeAndAtivo(String cidade, Boolean ativo);

    List<Estabelecimento> findAllByUfAndCidade(String uf, String cidade);

    List<Estabelecimento> findAllByUfAndCidadeAndAtivo(String uf, String cidade, Boolean ativo);

    boolean existsByCnpj(String cnpj);
}
