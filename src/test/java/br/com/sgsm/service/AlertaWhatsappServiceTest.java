package br.com.sgsm.service;

import br.com.sgsm.domain.Administrador;
import br.com.sgsm.domain.AdministradorEstabelecimento;
import br.com.sgsm.domain.Estabelecimento;
import br.com.sgsm.domain.WhatsappCanal;
import br.com.sgsm.repository.AdministradorEstabelecimentoRepository;
import br.com.sgsm.repository.AdministradorRepository;
import br.com.sgsm.repository.EstabelecimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Fase 6: alerta por e-mail quando o WhatsApp da clínica desconecta
@ExtendWith(MockitoExtension.class)
class AlertaWhatsappServiceTest {

    @Mock private ObjectProvider<JavaMailSender> provider;
    @Mock private JavaMailSender mailSender;
    @Mock private AdministradorEstabelecimentoRepository vinculos;
    @Mock private AdministradorRepository administradores;
    @Mock private EstabelecimentoRepository estabelecimentos;

    private final UUID clinica = UUID.randomUUID();
    private WhatsappCanal canal;

    @BeforeEach
    void setUp() {
        canal = new WhatsappCanal();
        canal.setEstabelecimentoId(clinica);
        canal.setInstancia("sgsm-abc");
        canal.setNumeroE164("5561999990000");
    }

    private AlertaWhatsappService service(String usuario, String senha) {
        return new AlertaWhatsappService(provider, vinculos, administradores, estabelecimentos,
                usuario, senha, "http://localhost:3001");
    }

    private static Administrador admin(String email, boolean ativo) {
        var a = new Administrador();
        a.setEmail(email);
        a.setAtivo(ativo);
        return a;
    }

    private void clinicaComDoisAdmins() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        var e = new Estabelecimento();
        e.setNome("Clinica Sao Lucas");
        e.setEmail("contato@saolucas.com");
        when(estabelecimentos.findById(clinica)).thenReturn(Optional.of(e));
        when(vinculos.findById_EstabelecimentoIdAndAtivo(clinica, true)).thenReturn(List.of(
                new AdministradorEstabelecimento(a1, clinica), new AdministradorEstabelecimento(a2, clinica)));
        when(administradores.findAllById(List.of(a1, a2))).thenReturn(List.of(
                admin("ana@saolucas.com", true), admin("inativo@saolucas.com", false),
                admin("contato@saolucas.com", true)));
    }

    @Test
    void enviaAosAdminsAtivosEAoEmailDaClinicaSemRepetir() {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        clinicaComDoisAdmins();

        service("alertas@sgsm.com", "senha").numeroDesconectado(canal);

        var enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(enviado.capture());
        var m = enviado.getValue();
        assertThat(m.getTo()).containsExactly("ana@saolucas.com", "contato@saolucas.com");
        assertThat(m.getFrom()).isEqualTo("alertas@sgsm.com");
        assertThat(m.getSubject()).contains("Clinica Sao Lucas").contains("desconectou");
        assertThat(m.getText()).contains("+5561999990000").contains("http://localhost:3001/whatsapp-clinica");
    }

    @Test
    void semSmtpConfiguradoFicaSoNoLog() {
        when(provider.getIfAvailable()).thenReturn(mailSender, (JavaMailSender) null);

        service("alertas@sgsm.com", "").numeroDesconectado(canal);
        service("alertas@sgsm.com", "senha").numeroDesconectado(canal);

        verifyNoInteractions(mailSender, vinculos, estabelecimentos);
    }

    @Test
    void clinicaSemDestinatarioNaoEnvia() {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        when(estabelecimentos.findById(clinica)).thenReturn(Optional.empty());
        when(vinculos.findById_EstabelecimentoIdAndAtivo(clinica, true)).thenReturn(List.of());
        when(administradores.findAllById(List.of())).thenReturn(List.of());

        service("alertas@sgsm.com", "senha").numeroDesconectado(canal);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void falhaDoSmtpOuDoBancoNaoDerrubaOMonitor() {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        clinicaComDoisAdmins();
        doThrow(new MailSendException("smtp fora")).when(mailSender).send(any(SimpleMailMessage.class));

        service("alertas@sgsm.com", "senha").numeroDesconectado(canal);

        when(estabelecimentos.findById(clinica)).thenThrow(new IllegalStateException("banco fora"));
        canal.setNumeroE164(null);
        service("alertas@sgsm.com", "senha").numeroDesconectado(canal);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
