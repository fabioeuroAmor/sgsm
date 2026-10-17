package br.com.sgsm.service;

import br.com.sgsm.exception.HorarioIndisponivelException;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.service.ReservaHorarioService.Reserva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.*;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaHorarioServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final Instant AGORA = Instant.parse("2026-10-16T13:00:00Z");
    private static final UUID MEDICO = UUID.randomUUID();
    private static final UUID SERVICO = UUID.randomUUID();
    private static final OffsetDateTime DEZ = OffsetDateTime.of(2026, 10, 19, 10, 0, 0, 0, ZoneOffset.ofHours(-3));

    @Mock private StringRedisTemplate redis;
    @Mock private ZSetOperations<String, String> zset;
    @Mock private HashOperations<String, Object, Object> hash;
    @Mock private ValueOperations<String, String> valores;

    private ReservaHorarioService service;

    @BeforeEach
    void setUp() {
        service = new ReservaHorarioService(redis, Clock.fixed(AGORA, ZONA));
        lenient().when(redis.opsForZSet()).thenReturn(zset);
        lenient().when(redis.<Object, Object>opsForHash()).thenReturn(hash);
        lenient().when(redis.opsForValue()).thenReturn(valores);
    }

    private static Map<Object, Object> campos(String dono, OffsetDateTime inicio, Instant criadaEm, Instant expiraEm) {
        Map<Object, Object> m = new HashMap<>();
        m.put("medicoId", MEDICO.toString());
        m.put("servicoMedicoId", SERVICO.toString());
        m.put("estabelecimentoId", "");
        m.put("tipo", "PRESENCIAL");
        m.put("inicio", inicio.toString());
        m.put("fim", inicio.plusMinutes(30).toString());
        m.put("dono", dono);
        m.put("criadaEm", criadaEm.toString());
        m.put("expiraEm", expiraEm.toString());
        return m;
    }

    private void existe(String token, String dono, OffsetDateTime inicio) {
        lenient().when(hash.entries(ReservaHorarioService.PREFIXO + token))
                .thenReturn(campos(dono, inicio, AGORA, AGORA.plusSeconds(600)));
    }

    private void travaLivre() {
        when(valores.setIfAbsent(eq(ReservaHorarioService.TRAVA + MEDICO), anyString(), eq(ReservaHorarioService.TTL_TRAVA)))
                .thenReturn(true);
    }

    // ── consulta ──────────────────────────────────────────────────────────────

    @Test
    void buscarDeveMontarAReservaOuVazio() {
        existe("t1", "secretaria", DEZ);
        when(hash.entries(ReservaHorarioService.PREFIXO + "nada")).thenReturn(Map.of());

        Reserva r = service.buscar("t1").orElseThrow();

        assertThat(r.medicoId()).isEqualTo(MEDICO);
        assertThat(r.estabelecimentoId()).isNull();
        assertThat(r.fim()).isEqualTo(DEZ.plusMinutes(30));
        assertThat(service.buscar("nada")).isEmpty();
    }

    @Test
    void ativasDoMedicoDeveLimparTokensExpiradosERetornarSoOsSobrepostos() {
        when(zset.rangeByScore(eq(ReservaHorarioService.INDICE_MEDICO + MEDICO), anyDouble(), anyDouble()))
                .thenReturn(new LinkedHashSet<>(List.of("viva", "expirada", "outro-horario")));
        existe("viva", "bot", DEZ);
        existe("outro-horario", "bot", DEZ.plusHours(2));
        when(hash.entries(ReservaHorarioService.PREFIXO + "expirada")).thenReturn(Map.of());

        var ativas = service.ativasDoMedico(MEDICO, DEZ, DEZ.plusMinutes(30));

        assertThat(ativas).extracting(Reserva::token).containsExactly("viva");
        verify(zset).remove(ReservaHorarioService.INDICE_MEDICO + MEDICO, "expirada");
        assertThat(service.existeReservaDeOutro(MEDICO, DEZ, DEZ.plusMinutes(30), "secretaria")).isTrue();
        assertThat(service.existeReservaDeOutro(MEDICO, DEZ, DEZ.plusMinutes(30), "bot")).isFalse();
    }

    @Test
    void indiceInexistenteNaoTemReservas() {
        when(zset.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(null);

        assertThat(service.ativasDoMedico(MEDICO, DEZ, DEZ.plusMinutes(30))).isEmpty();
    }

    // ── criar ─────────────────────────────────────────────────────────────────

    @Test
    void criarDeveGravarComValidadeDeDezMinutosEIndexar() {
        travaLivre();
        when(zset.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of());
        when(zset.range(anyString(), eq(0L), eq(-1L))).thenReturn(Set.of());

        Reserva r = service.criar(MEDICO, SERVICO, null, "PRESENCIAL", DEZ, DEZ.plusMinutes(30), "secretaria");

        assertThat(r.expiraEm()).isEqualTo(AGORA.plus(ReservaHorarioService.VALIDADE));
        assertThat(r.renovavelAte()).isEqualTo(AGORA.plus(ReservaHorarioService.DURACAO_MAXIMA));
        verify(hash).putAll(eq(ReservaHorarioService.PREFIXO + r.token()), anyMap());
        verify(redis).expire(ReservaHorarioService.PREFIXO + r.token(), ReservaHorarioService.VALIDADE);
        verify(zset).add(ReservaHorarioService.INDICE_MEDICO + MEDICO, r.token(), DEZ.toEpochSecond());
        verify(zset).add(ReservaHorarioService.INDICE_DONO + "secretaria", r.token(), DEZ.toEpochSecond());
        verify(redis).expire(ReservaHorarioService.INDICE_MEDICO + MEDICO, ReservaHorarioService.TTL_INDICE);
        // trava sempre liberada no fim
        verify(redis).execute(any(), eq(List.of(ReservaHorarioService.TRAVA + MEDICO)), anyString());
    }

    @Test
    void criarSobreReservaDeOutroUsuarioDeveSerRecusado() {
        travaLivre();
        when(zset.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of("do-bot"));
        existe("do-bot", "bot", DEZ);

        assertThatThrownBy(() -> service.criar(MEDICO, SERVICO, null, "PRESENCIAL", DEZ, DEZ.plusMinutes(30), "secretaria"))
                .isInstanceOf(HorarioIndisponivelException.class);
        verify(hash, never()).putAll(anyString(), anyMap());
        verify(redis).execute(any(), anyList(), anyString());
    }

    @Test
    void limiteDeTresReservasAtivasPorUsuario() {
        travaLivre();
        when(zset.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(Set.of());
        when(zset.range(ReservaHorarioService.INDICE_DONO + "secretaria", 0, -1))
                .thenReturn(new LinkedHashSet<>(List.of("a", "b", "c", "expirada")));
        when(redis.hasKey(anyString())).thenAnswer(inv -> !inv.getArgument(0).toString().endsWith("expirada"));

        assertThatThrownBy(() -> service.criar(MEDICO, SERVICO, null, "PRESENCIAL", DEZ, DEZ.plusMinutes(30), "secretaria"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limite de 3");
        verify(zset).remove(ReservaHorarioService.INDICE_DONO + "secretaria", "expirada");
    }

    @Test
    void travaOcupadaPorMuitoTempoDevolveHorarioIndisponivel() {
        when(valores.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.criar(MEDICO, SERVICO, null, "PRESENCIAL", DEZ, DEZ.plusMinutes(30), "secretaria"))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("Tente novamente");
        verify(valores, times(ReservaHorarioService.TENTATIVAS_TRAVA)).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    // ── renovar / liberar / consumir ──────────────────────────────────────────

    @Test
    void renovarDaMaisDezMinutosSemPassarDeTrintaDesdeACriacao() {
        // criada há 25 min: renovar daria +10 min, mas o teto é 30 min desde a criação
        Instant criadaEm = AGORA.minus(Duration.ofMinutes(25));
        when(hash.entries(ReservaHorarioService.PREFIXO + "t1"))
                .thenReturn(campos("secretaria", DEZ, criadaEm, AGORA.plusSeconds(60)));

        Reserva r = service.renovar("t1", "secretaria");

        assertThat(r.expiraEm()).isEqualTo(criadaEm.plus(ReservaHorarioService.DURACAO_MAXIMA));
        var ttl = ArgumentCaptor.forClass(Duration.class);
        verify(redis).expire(eq(ReservaHorarioService.PREFIXO + "t1"), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void renovarNoInicioDaMaisDezMinutos() {
        existe("t1", "secretaria", DEZ);

        assertThat(service.renovar("t1", "secretaria").expiraEm()).isEqualTo(AGORA.plus(ReservaHorarioService.VALIDADE));
    }

    @Test
    void renovarDepoisDeTrintaMinutosEhRecusado() {
        when(hash.entries(ReservaHorarioService.PREFIXO + "t1"))
                .thenReturn(campos("secretaria", DEZ, AGORA.minus(Duration.ofMinutes(31)), AGORA.plusSeconds(60)));

        assertThatThrownBy(() -> service.renovar("t1", "secretaria"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 minutos");
    }

    // Reserva de outro usuário responde como inexistente
    @Test
    void soODonoRenovaOuLibera() {
        existe("t1", "bot", DEZ);

        assertThatThrownBy(() -> service.renovar("t1", "secretaria")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.liberar("t1", "secretaria")).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(redis, never()).delete(anyString());
    }

    @Test
    void liberarRemoveAReservaEOsIndices() {
        existe("t1", "secretaria", DEZ);

        service.liberar("t1", "secretaria");

        verify(redis).delete(ReservaHorarioService.PREFIXO + "t1");
        verify(zset).remove(ReservaHorarioService.INDICE_MEDICO + MEDICO, "t1");
        verify(zset).remove(ReservaHorarioService.INDICE_DONO + "secretaria", "t1");
    }

    @Test
    void consumoExigeMesmoMedicoEMesmoHorario() {
        existe("t1", "secretaria", DEZ);

        assertThat(service.validarParaConsumo("t1", "secretaria", MEDICO, DEZ).token()).isEqualTo("t1");
        assertThatThrownBy(() -> service.validarParaConsumo("t1", "secretaria", MEDICO, DEZ.plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validarParaConsumo("t1", "secretaria", UUID.randomUUID(), DEZ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noFusoConverteParaOFusoDoRelogio() {
        assertThat(service.noFuso(AGORA)).isEqualTo(OffsetDateTime.of(2026, 10, 16, 10, 0, 0, 0, ZoneOffset.ofHours(-3)));
    }
}
