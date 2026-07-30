package br.com.sgsm.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

// Redis PAPEL 3 (ver desenho de solucao): fila de notificacoes proativas do WhatsApp
// (confirmacao de agendamento, lembrete de consulta, recuperacao de inativos).
// Mesmo padrao do VetorizacaoPublisher: publicado apos o commit no PostgreSQL, falha
// no Redis nao reverte a transacao de negocio.
@Component
public class NotificacaoPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoPublisher.class);
    private static final String STREAM_KEY = "sgsm:events:notificacao";

    private final StringRedisTemplate redis;

    public NotificacaoPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publicar(String tipoNotificacao, String agendamentoId, String pacienteId) {
        try {
            var campos = Map.of(
                    "tipoNotificacao", tipoNotificacao,
                    "agendamentoId", agendamentoId == null ? "" : agendamentoId,
                    "pacienteId", pacienteId,
                    "timestamp", Instant.now().toString()
            );
            redis.opsForStream().add(StreamRecords.mapBacked(campos).withStreamKey(STREAM_KEY));
        } catch (Exception e) {
            log.warn("Falha ao publicar notificacao tipo={} pacienteId={}: {}", tipoNotificacao, pacienteId, e.getMessage());
        }
    }
}
