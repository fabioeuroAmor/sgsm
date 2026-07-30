package br.com.sgsm.whatsapp.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Redis PAPEL 4 (ver desenho de solucao): limita a 10 mensagens/min por telefone no canal
// WhatsApp. Estourou o limite -> quem chama deve responder uma mensagem de aguardo e NAO
// chamar o RAG (evita abuso/spam gerando custo de LLM sem necessidade).
@Service
public class RateLimitService {

    private static final String PREFIXO = "ratelimit:";
    private static final long LIMITE_POR_MINUTO = 10;
    private static final Duration JANELA = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean excedeuLimite(String telefone) {
        String chave = PREFIXO + telefone;
        Long contagem = redis.opsForValue().increment(chave);
        if (contagem != null && contagem == 1L) {
            redis.expire(chave, JANELA);
        }
        return contagem != null && contagem > LIMITE_POR_MINUTO;
    }
}
