package br.com.sgsm.whatsapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        service = new RateLimitService(redis);
    }

    @Test
    void naoExcedeLimiteAbaixoDoTeto() {
        when(valueOperations.increment("ratelimit:5561999998888")).thenReturn(3L);

        assertThat(service.excedeuLimite("5561999998888")).isFalse();
    }

    @Test
    void excedeLimiteAcimaDeDezMensagensPorMinuto() {
        when(valueOperations.increment("ratelimit:5561999998888")).thenReturn(11L);

        assertThat(service.excedeuLimite("5561999998888")).isTrue();
    }

    @Test
    void definePrazoDeExpiracaoNaPrimeiraMensagemDaJanela() {
        when(valueOperations.increment("ratelimit:5561999998888")).thenReturn(1L);

        service.excedeuLimite("5561999998888");

        verify(redis).expire(eq("ratelimit:5561999998888"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void naoRedefineExpiracaoAposAPrimeiraMensagem() {
        when(valueOperations.increment("ratelimit:5561999998888")).thenReturn(2L);

        service.excedeuLimite("5561999998888");

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }
}
