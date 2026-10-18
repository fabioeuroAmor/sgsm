package br.com.sgsm.service;

import br.com.sgsm.exception.HorarioIndisponivelException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Pré-reserva temporária de horário (Plano_Desenvolvimento_Conflito_Agendamento_SGSM.docx, Fase 3):
// enquanto a secretária (ou o paciente, no chatbot/WhatsApp) finaliza, o horário some da lista de
// livres para os outros canais. Fica no Redis e expira sozinha (DA5).
//  - validade de 10 min, renovável pelo dono até 30 min desde a criação (DA1)
//  - no máximo 3 pré-reservas ativas por usuário (DA4)
// É um aviso entre canais, não uma garantia: quem impede dois agendamentos no mesmo horário é a
// constraint excl_sem_conflito_medico (V013).
@Service
public class ReservaHorarioService {

    static final Duration VALIDADE = Duration.ofMinutes(10);
    static final Duration DURACAO_MAXIMA = Duration.ofMinutes(30);
    static final int LIMITE_POR_USUARIO = 3;

    static final String PREFIXO = "sgsm:reserva:";
    static final String INDICE_MEDICO = "sgsm:reservas:medico:";
    static final String INDICE_DONO = "sgsm:reservas:dono:";
    static final String TRAVA = "sgsm:trava:reserva:";
    static final Duration TTL_TRAVA = Duration.ofSeconds(5);
    // Renovado a cada reserva criada: como nenhuma reserva vive mais que DURACAO_MAXIMA, o índice
    // some logo depois da última reserva expirar (sem deixar token órfão no Redis)
    static final Duration TTL_INDICE = DURACAO_MAXIMA.plusMinutes(1);
    static final int TENTATIVAS_TRAVA = 20;

    private static final RedisScript<Long> LIBERAR_SE_DONO = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    public record Reserva(String token, UUID medicoId, UUID servicoMedicoId, UUID estabelecimentoId, String tipo,
                          OffsetDateTime inicio, OffsetDateTime fim, String dono, Instant criadaEm, Instant expiraEm) {

        boolean sobrepoe(OffsetDateTime outroInicio, OffsetDateTime outroFim) {
            return inicio.isBefore(outroFim) && fim.isAfter(outroInicio);
        }

        public Instant renovavelAte() {
            return criadaEm.plus(DURACAO_MAXIMA);
        }
    }

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final ZoneId zona;

    public ReservaHorarioService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
        this.zona = clock.getZone();
    }

    // ── consulta ──────────────────────────────────────────────────────────────

    // Pré-reservas vivas do médico que se sobrepõem ao período. Índices com tokens expirados são
    // limpos aqui (a chave da reserva some sozinha pelo TTL; o índice não)
    public List<Reserva> ativasDoMedico(UUID medicoId, OffsetDateTime de, OffsetDateTime ate) {
        String indice = INDICE_MEDICO + medicoId;
        // reservas começam no máximo um dia antes do fim do período consultado
        Set<String> tokens = redis.opsForZSet().rangeByScore(indice,
                de.minusDays(1).toEpochSecond(), ate.toEpochSecond());
        List<Reserva> ativas = new ArrayList<>();
        if (tokens == null) {
            return ativas;
        }
        for (String token : tokens) {
            Optional<Reserva> reserva = buscar(token);
            if (reserva.isEmpty()) {
                redis.opsForZSet().remove(indice, token);
            } else if (reserva.get().sobrepoe(de, ate)) {
                ativas.add(reserva.get());
            }
        }
        return ativas;
    }

    public boolean existeReservaDeOutro(UUID medicoId, OffsetDateTime inicio, OffsetDateTime fim, String dono) {
        return ativasDoMedico(medicoId, inicio, fim).stream().anyMatch(r -> !r.dono().equals(dono));
    }

    public Optional<Reserva> buscar(String token) {
        Map<Object, Object> campos = redis.opsForHash().entries(PREFIXO + token);
        if (campos == null || campos.isEmpty()) {
            return Optional.empty();
        }
        String estabelecimento = (String) campos.get("estabelecimentoId");
        return Optional.of(new Reserva(token,
                UUID.fromString((String) campos.get("medicoId")),
                UUID.fromString((String) campos.get("servicoMedicoId")),
                estabelecimento == null || estabelecimento.isEmpty() ? null : UUID.fromString(estabelecimento),
                (String) campos.get("tipo"),
                OffsetDateTime.parse((String) campos.get("inicio")),
                OffsetDateTime.parse((String) campos.get("fim")),
                (String) campos.get("dono"),
                Instant.parse((String) campos.get("criadaEm")),
                Instant.parse((String) campos.get("expiraEm"))));
    }

    // ── criar / renovar / liberar ─────────────────────────────────────────────

    // Quem chama já validou escopo, serviço e que o horário está livre no banco. Aqui: trava curta
    // por médico (duas reservas simultâneas não passam juntas), conflito com reserva de outro e limite
    public Reserva criar(UUID medicoId, UUID servicoMedicoId, UUID estabelecimentoId, String tipo,
                         OffsetDateTime inicio, OffsetDateTime fim, String dono) {
        String trava = TRAVA + medicoId;
        String donoDaTrava = adquirirTrava(trava);
        try {
            if (existeReservaDeOutro(medicoId, inicio, fim, dono)) {
                throw new HorarioIndisponivelException(
                        "Horário em pré-reserva por outro usuário. Escolha outro horário.", List.of());
            }
            if (ativasDoDono(dono) >= LIMITE_POR_USUARIO) {
                throw new IllegalArgumentException("Limite de " + LIMITE_POR_USUARIO
                        + " pré-reservas ativas por usuário. Finalize ou libere uma antes de reservar outra.");
            }
            Instant agora = clock.instant();
            var reserva = new Reserva(UUID.randomUUID().toString(), medicoId, servicoMedicoId, estabelecimentoId,
                    tipo, inicio, fim, dono, agora, agora.plus(VALIDADE));
            gravar(reserva);
            redis.opsForZSet().add(INDICE_MEDICO + medicoId, reserva.token(), inicio.toEpochSecond());
            redis.opsForZSet().add(INDICE_DONO + dono, reserva.token(), inicio.toEpochSecond());
            // Índices também expiram (são renovados a cada reserva nova): sem isso cresceriam para
            // sempre em médicos/usuários que ninguém mais consulta
            redis.expire(INDICE_MEDICO + medicoId, TTL_INDICE);
            redis.expire(INDICE_DONO + dono, TTL_INDICE);
            return reserva;
        } finally {
            redis.execute(LIBERAR_SE_DONO, List.of(trava), donoDaTrava);
        }
    }

    // Só o dono; cada renovação dá mais 10 min, sem passar de 30 min desde a criação
    public Reserva renovar(String token, String dono) {
        Reserva atual = doDono(token, dono);
        Instant agora = clock.instant();
        if (!agora.isBefore(atual.renovavelAte())) {
            throw new IllegalArgumentException("A pré-reserva atingiu o limite de "
                    + DURACAO_MAXIMA.toMinutes() + " minutos e não pode mais ser renovada.");
        }
        Instant novaExpiracao = agora.plus(VALIDADE);
        if (novaExpiracao.isAfter(atual.renovavelAte())) {
            novaExpiracao = atual.renovavelAte();
        }
        var renovada = new Reserva(atual.token(), atual.medicoId(), atual.servicoMedicoId(), atual.estabelecimentoId(),
                atual.tipo(), atual.inicio(), atual.fim(), atual.dono(), atual.criadaEm(), novaExpiracao);
        gravar(renovada);
        return renovada;
    }

    public void liberar(String token, String dono) {
        remover(doDono(token, dono));
    }

    // Usada ao gravar o agendamento: a reserva precisa ser do próprio usuário e do mesmo horário
    public Reserva validarParaConsumo(String token, String dono, UUID medicoId, OffsetDateTime inicio) {
        Reserva reserva = doDono(token, dono);
        if (!reserva.medicoId().equals(medicoId) || !reserva.inicio().isEqual(inicio)) {
            throw new IllegalArgumentException("A pré-reserva informada é de outro horário.");
        }
        return reserva;
    }

    public void remover(Reserva reserva) {
        redis.delete(PREFIXO + reserva.token());
        redis.opsForZSet().remove(INDICE_MEDICO + reserva.medicoId(), reserva.token());
        redis.opsForZSet().remove(INDICE_DONO + reserva.dono(), reserva.token());
    }

    public OffsetDateTime noFuso(Instant instante) {
        return instante.atZone(zona).toOffsetDateTime();
    }

    // ── auxiliares ────────────────────────────────────────────────────────────

    // Reserva de outro usuário responde como inexistente (404), sem confirmar que existe
    private Reserva doDono(String token, String dono) {
        return buscar(token)
                .filter(r -> r.dono().equals(dono))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pré-reserva não encontrada ou expirada"));
    }

    private long ativasDoDono(String dono) {
        String indice = INDICE_DONO + dono;
        Set<String> tokens = redis.opsForZSet().range(indice, 0, -1);
        long ativas = 0;
        if (tokens == null) {
            return 0;
        }
        for (String token : tokens) {
            if (Boolean.TRUE.equals(redis.hasKey(PREFIXO + token))) {
                ativas++;
            } else {
                redis.opsForZSet().remove(indice, token);
            }
        }
        return ativas;
    }

    private void gravar(Reserva r) {
        Map<String, String> campos = new HashMap<>();
        campos.put("medicoId", r.medicoId().toString());
        campos.put("servicoMedicoId", r.servicoMedicoId().toString());
        campos.put("estabelecimentoId", r.estabelecimentoId() == null ? "" : r.estabelecimentoId().toString());
        campos.put("tipo", r.tipo());
        campos.put("inicio", r.inicio().toString());
        campos.put("fim", r.fim().toString());
        campos.put("dono", r.dono());
        campos.put("criadaEm", r.criadaEm().toString());
        campos.put("expiraEm", r.expiraEm().toString());
        String chave = PREFIXO + r.token();
        redis.opsForHash().putAll(chave, campos);
        redis.expire(chave, Duration.between(clock.instant(), r.expiraEm()));
    }

    private String adquirirTrava(String chave) {
        String dono = UUID.randomUUID().toString();
        for (int i = 0; i < TENTATIVAS_TRAVA; i++) {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(chave, dono, TTL_TRAVA))) {
                return dono;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new HorarioIndisponivelException("A agenda deste médico está sendo reservada agora. Tente novamente.",
                List.of());
    }
}
