package br.com.sgsm.whatsapp.dto.received;

// Payload do webhook da Evolution API para o evento "messages.upsert".
// ATENCAO: os nomes de campo seguem o contrato documentado/mais comum da Evolution API —
// confirmar contra a instancia real (versao pode variar) quando ela for configurada.
public record WhatsAppWebhookRequest(String event, String instance, Dados data) {

    public record Dados(Key key, String pushName, Mensagem message) {}

    public record Key(String remoteJid, Boolean fromMe, String id) {}

    public record Mensagem(String conversation) {}

    public String telefoneRemetente() {
        if (data == null || data.key() == null || data.key().remoteJid() == null) {
            return null;
        }
        return data.key().remoteJid().split("@")[0];
    }

    public String texto() {
        return data != null && data.message() != null ? data.message().conversation() : null;
    }

    public String nomeExibicao() {
        return data != null ? data.pushName() : null;
    }

    // Ignora eco de mensagens enviadas pela propria instancia e eventos sem texto (ex.: imagem,
    // status de entrega, mensagem de sistema).
    public boolean ehMensagemRecebidaValida() {
        return data != null && data.key() != null && Boolean.FALSE.equals(data.key().fromMe())
                && texto() != null && !texto().isBlank();
    }
}
