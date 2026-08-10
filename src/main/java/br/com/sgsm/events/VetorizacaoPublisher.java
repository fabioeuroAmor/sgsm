package br.com.sgsm.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class VetorizacaoPublisher {

    private static final Logger log = LoggerFactory.getLogger(VetorizacaoPublisher.class);
    private static final String STREAM_KEY = "sgsm:events:vetorizacao";

    private final StringRedisTemplate redis;

    public VetorizacaoPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // Chamado APÓS o commit no PostgreSQL. Falha no Redis NÃO reverte a transação.
    public void publicar(String tipo, String id, String operacao) {
        try {
            var campos = Map.of(
                    "tipo",      tipo,
                    "id",        id,
                    "operacao",  operacao,
                    "timestamp", Instant.now().toString()
            );
            redis.opsForStream().add(StreamRecords.mapBacked(campos).withStreamKey(STREAM_KEY));
        } catch (Exception e) {
            log.warn("Falha ao publicar evento de vetorizacao tipo={} id={}: {}", tipo, id, e.getMessage());
        }
    }
}
