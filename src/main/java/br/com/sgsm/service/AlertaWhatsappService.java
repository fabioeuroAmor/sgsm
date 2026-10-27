package br.com.sgsm.service;

import br.com.sgsm.domain.Administrador;
import br.com.sgsm.domain.WhatsappCanal;
import br.com.sgsm.repository.AdministradorEstabelecimentoRepository;
import br.com.sgsm.repository.AdministradorRepository;
import br.com.sgsm.repository.EstabelecimentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

// Fase 6 do plano WhatsApp por clínica: quando o número de uma clínica cai, os admins ativos dela
// (e o e-mail da clínica, se houver) recebem um e-mail para ler o QR code de novo. Sem SMTP
// configurado, o alerta fica só no log — o monitor nunca falha por causa do e-mail.
@Service
public class AlertaWhatsappService {

    private static final Logger log = LoggerFactory.getLogger(AlertaWhatsappService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository;
    private final AdministradorRepository administradorRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final String remetente;
    private final String senhaSmtp;
    private final String appUrl;

    public AlertaWhatsappService(ObjectProvider<JavaMailSender> mailSender,
                                 AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository,
                                 AdministradorRepository administradorRepository,
                                 EstabelecimentoRepository estabelecimentoRepository,
                                 @Value("${spring.mail.username:}") String remetente,
                                 @Value("${spring.mail.password:}") String senhaSmtp,
                                 @Value("${app.url:http://localhost:3001}") String appUrl) {
        this.mailSender = mailSender;
        this.administradorEstabelecimentoRepository = administradorEstabelecimentoRepository;
        this.administradorRepository = administradorRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.remetente = remetente;
        this.senhaSmtp = senhaSmtp;
        this.appUrl = appUrl;
    }

    public void numeroDesconectado(WhatsappCanal canal) {
        log.warn("ALERTA: o WhatsApp da clínica {} ({}) desconectou — o admin precisa ler o QR code de novo",
                canal.getEstabelecimentoId(), canal.getNumeroE164());
        try {
            enviar(canal);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar o alerta por e-mail do WhatsApp da clínica {}: {}",
                    canal.getEstabelecimentoId(), e.getMessage());
        }
    }

    private void enviar(WhatsappCanal canal) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || remetente.isBlank() || senhaSmtp.isBlank()) {
            log.info("SMTP não configurado no sgsm (SMTP_USERNAME/SMTP_PASSWORD): alerta do WhatsApp {} só no log",
                    canal.getInstancia());
            return;
        }
        var estabelecimento = estabelecimentoRepository.findById(canal.getEstabelecimentoId()).orElse(null);
        String nomeClinica = estabelecimento == null ? "sua clínica" : estabelecimento.getNome();

        Set<String> destinatarios = new LinkedHashSet<>();
        var ids = administradorEstabelecimentoRepository
                .findById_EstabelecimentoIdAndAtivo(canal.getEstabelecimentoId(), true)
                .stream().map(v -> v.getId().getAdministradorId()).toList();
        administradorRepository.findAllById(ids).stream()
                .filter(a -> Boolean.TRUE.equals(a.getAtivo()))
                .map(Administrador::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .forEach(destinatarios::add);
        if (estabelecimento != null && estabelecimento.getEmail() != null && !estabelecimento.getEmail().isBlank()) {
            destinatarios.add(estabelecimento.getEmail());
        }
        if (destinatarios.isEmpty()) {
            log.warn("Clínica {} sem admin ativo nem e-mail: alerta do WhatsApp só no log", canal.getEstabelecimentoId());
            return;
        }

        var mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(destinatarios.toArray(String[]::new));
        mensagem.setSubject("SGSM — O WhatsApp da " + nomeClinica + " desconectou");
        mensagem.setText(corpo(nomeClinica, canal.getNumeroE164()));
        try {
            sender.send(mensagem);
            log.info("Alerta de WhatsApp desconectado enviado a {} destinatário(s) da clínica {}",
                    destinatarios.size(), canal.getEstabelecimentoId());
        } catch (MailException e) {
            log.error("Falha ao enviar o alerta por e-mail do WhatsApp da clínica {}: {}",
                    canal.getEstabelecimentoId(), e.getMessage());
        }
    }

    private String corpo(String nomeClinica, String numero) {
        return "Olá,\n\n"
                + "O WhatsApp da " + nomeClinica + (numero == null ? "" : " (+" + numero + ")")
                + " desconectou do SGSM. Enquanto ele estiver desconectado, os pacientes que escreverem"
                + " para esse número não serão atendidos pelo assistente.\n\n"
                + "Para reconectar, entre no SGSM com o usuário de administrador da clínica, abra"
                + " \"WhatsApp da clínica\" e leia o QR code com o celular da clínica:\n"
                + appUrl + "/whatsapp-clinica\n\n"
                + "Equipe SGSM";
    }
}
