package br.com.sgsm.whatsapp.dto.received;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookRequestTest {

    private WhatsAppWebhookRequest requisicao(boolean fromMe, String texto, String pushName) {
        var key = new WhatsAppWebhookRequest.Key("5561999998888@s.whatsapp.net", fromMe, "msg-1");
        var message = texto == null ? null : new WhatsAppWebhookRequest.Mensagem(texto);
        var data = new WhatsAppWebhookRequest.Dados(key, pushName, message);
        return new WhatsAppWebhookRequest("messages.upsert", "sgsm", data);
    }

    @Test
    void extraiTelefoneRemetenteDoRemoteJid() {
        assertThat(requisicao(false, "oi", "Ana").telefoneRemetente()).isEqualTo("5561999998888");
    }

    @Test
    void extraiTextoDaMensagem() {
        assertThat(requisicao(false, "Qual meu histórico?", "Ana").texto()).isEqualTo("Qual meu histórico?");
    }

    @Test
    void ehMensagemRecebidaValidaQuandoNaoEhEcoETemTexto() {
        assertThat(requisicao(false, "oi", "Ana").ehMensagemRecebidaValida()).isTrue();
    }

    @Test
    void naoEhMensagemRecebidaValidaQuandoEhEcoDaPropriaInstancia() {
        assertThat(requisicao(true, "oi", "Ana").ehMensagemRecebidaValida()).isFalse();
    }

    @Test
    void naoEhMensagemRecebidaValidaQuandoSemTexto() {
        assertThat(requisicao(false, null, "Ana").ehMensagemRecebidaValida()).isFalse();
    }

    @Test
    void naoEhMensagemRecebidaValidaQuandoTextoEmBranco() {
        assertThat(requisicao(false, "   ", "Ana").ehMensagemRecebidaValida()).isFalse();
    }

    @Test
    void retornaNullQuandoDadosAusentes() {
        var vazio = new WhatsAppWebhookRequest("event", "instance", null);

        assertThat(vazio.telefoneRemetente()).isNull();
        assertThat(vazio.texto()).isNull();
        assertThat(vazio.nomeExibicao()).isNull();
        assertThat(vazio.ehMensagemRecebidaValida()).isFalse();
    }
}
