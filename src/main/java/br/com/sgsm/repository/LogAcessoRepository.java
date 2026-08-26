package br.com.sgsm.repository;

import br.com.sgsm.domain.LogAcesso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LogAcessoRepository extends JpaRepository<LogAcesso, UUID> {
}
