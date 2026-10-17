package br.com.sgsm.exception;

import br.com.sgsm.dto.SlotDisponivelResponse;

import java.util.List;

// Horário já ocupado ou pré-reservado por outro usuário. Vira 409 com os horários livres mais
// próximos daquele médico, para quem chegou em segundo escolher outro na hora.
// Estende IllegalArgumentException para manter compatibilidade com quem já tratava o conflito
// como argumento inválido.
public class HorarioIndisponivelException extends IllegalArgumentException {

    private final transient List<SlotDisponivelResponse> alternativas;

    public HorarioIndisponivelException(String mensagem, List<SlotDisponivelResponse> alternativas) {
        super(mensagem);
        this.alternativas = alternativas == null ? List.of() : List.copyOf(alternativas);
    }

    public List<SlotDisponivelResponse> getAlternativas() {
        return alternativas;
    }
}
