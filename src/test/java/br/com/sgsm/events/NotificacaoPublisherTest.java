package br.com.sgsm.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoPublisherTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private NotificacaoPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificacaoPublisher(redis);
    }

    @Test
    void devePublicarNoStreamDeNotificacao() {
        when(redis.opsForStream()).thenReturn(streamOperations);

        publisher.publicar("CONFIRMACAO_AGENDAMENTO", "agendamento-1", "paciente-1");

        verify(streamOperations).add(any());
    }

    @Test
    void naoDeveLancarExcecaoQuandoRedisFalha() {
        when(redis.opsForStream()).thenThrow(new RuntimeException("redis fora do ar"));

        assertThatCode(() -> publisher.publicar("LEMBRETE_CONSULTA", "agendamento-1", "paciente-1"))
                .doesNotThrowAnyException();
    }
}
