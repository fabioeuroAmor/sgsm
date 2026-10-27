package br.com.sgsm.repository;

import br.com.sgsm.domain.WhatsappCanal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhatsappCanalRepository extends JpaRepository<WhatsappCanal, UUID> {

    Optional<WhatsappCanal> findByInstancia(String instancia);

    Optional<WhatsappCanal> findFirstByEstabelecimentoIdAndStatusNot(UUID estabelecimentoId, String status);

    List<WhatsappCanal> findAllByStatusNot(String status);

    List<WhatsappCanal> findAllByStatus(String status);
}
