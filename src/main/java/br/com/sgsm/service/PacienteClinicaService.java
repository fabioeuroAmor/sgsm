package br.com.sgsm.service;

import br.com.sgsm.domain.ConsentimentoLgpd;
import br.com.sgsm.domain.PacienteEstabelecimento;
import br.com.sgsm.dto.ConsentimentoRequest;
import br.com.sgsm.dto.VinculoPacienteClinicaRequest;
import br.com.sgsm.exception.RecursoNaoEncontradoException;
import br.com.sgsm.repository.ConsentimentoLgpdRepository;
import br.com.sgsm.repository.EstabelecimentoRepository;
import br.com.sgsm.repository.PacienteEstabelecimentoRepository;
import br.com.sgsm.repository.PacienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Vínculo paciente x clínica (DW5) e consentimento LGPD por clínica e canal (DW6) — Fases 4 e 5 do
// plano de WhatsApp por clínica
@Service
@Transactional
public class PacienteClinicaService {

    static final Set<String> ORIGENS = Set.of("WHATSAPP", "SITE", "CLINICA");

    private final PacienteEstabelecimentoRepository vinculoRepository;
    private final ConsentimentoLgpdRepository consentimentoRepository;
    private final PacienteRepository pacienteRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;

    public PacienteClinicaService(PacienteEstabelecimentoRepository vinculoRepository,
                                  ConsentimentoLgpdRepository consentimentoRepository,
                                  PacienteRepository pacienteRepository,
                                  EstabelecimentoRepository estabelecimentoRepository) {
        this.vinculoRepository = vinculoRepository;
        this.consentimentoRepository = consentimentoRepository;
        this.pacienteRepository = pacienteRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
    }

    // Idempotente: o primeiro contato fica registrado; um novo contato pela mesma clínica não muda a origem
    public void vincular(VinculoPacienteClinicaRequest request) {
        if (request.pacienteId() == null || request.estabelecimentoId() == null) {
            throw new IllegalArgumentException("Informe o paciente e o estabelecimento.");
        }
        String origem = request.origem() == null ? null : request.origem().toUpperCase();
        if (!ORIGENS.contains(origem)) {
            throw new IllegalArgumentException("Origem inválida. Valores aceitos: " + ORIGENS);
        }
        exigirPacienteEClinica(request.pacienteId(), request.estabelecimentoId());
        var id = new PacienteEstabelecimento.Id(request.pacienteId(), request.estabelecimentoId());
        if (!vinculoRepository.existsById(id)) {
            vinculoRepository.save(new PacienteEstabelecimento(request.pacienteId(), request.estabelecimentoId(), origem));
        }
        if (request.telefone() != null && !request.telefone().isBlank()) {
            for (var c : consentimentoRepository.findAllByTelefoneAndEstabelecimentoIdAndRevogadoEmIsNull(
                    normalizarTelefone(request.telefone()), request.estabelecimentoId())) {
                if (c.getPacienteId() == null) {
                    c.setPacienteId(request.pacienteId());
                    consentimentoRepository.save(c);
                }
            }
        }
    }

    // Vincula o paciente cadastrado pela equipe às clínicas dela (origem CLINICA): sem isso ele só
    // aparecia para a clínica depois do primeiro agendamento
    public void vincularAsClinicas(UUID pacienteId, Collection<UUID> estabelecimentos) {
        for (UUID estabelecimento : estabelecimentos) {
            var id = new PacienteEstabelecimento.Id(pacienteId, estabelecimento);
            if (!vinculoRepository.existsById(id)) {
                vinculoRepository.save(new PacienteEstabelecimento(pacienteId, estabelecimento, "CLINICA"));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> pacientesDasClinicas(Collection<UUID> estabelecimentos) {
        return estabelecimentos.isEmpty() ? List.of() : vinculoRepository.findPacienteIdsByEstabelecimentoIdIn(estabelecimentos);
    }

    public void registrarConsentimento(ConsentimentoRequest request) {
        String canal = request.canal() == null ? null : request.canal().toUpperCase();
        if (!ORIGENS.contains(canal)) {
            throw new IllegalArgumentException("Canal inválido. Valores aceitos: " + ORIGENS);
        }
        if (request.estabelecimentoId() == null || !estabelecimentoRepository.existsById(request.estabelecimentoId())) {
            throw new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + request.estabelecimentoId());
        }
        String telefone = request.telefone() == null || request.telefone().isBlank() ? null : normalizarTelefone(request.telefone());
        if (telefone == null && request.pacienteId() == null) {
            throw new IllegalArgumentException("Informe o telefone ou o paciente do consentimento.");
        }
        if (request.versaoTexto() == null || request.versaoTexto().isBlank()) {
            throw new IllegalArgumentException("Informe a versão do termo aceito.");
        }
        var c = new ConsentimentoLgpd();
        c.setTelefone(telefone);
        c.setPacienteId(request.pacienteId());
        c.setEstabelecimentoId(request.estabelecimentoId());
        c.setCanal(canal);
        c.setVersaoTexto(request.versaoTexto());
        consentimentoRepository.save(c);
    }

    @Transactional(readOnly = true)
    public boolean consentimentoVigente(String telefone, UUID estabelecimentoId, String canal) {
        if (telefone == null || estabelecimentoId == null || canal == null) return false;
        return consentimentoRepository.existsByTelefoneAndEstabelecimentoIdAndCanalAndRevogadoEmIsNull(
                normalizarTelefone(telefone), estabelecimentoId, canal.toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<ConsentimentoLgpd> consentimentosDoPaciente(UUID pacienteId) {
        return consentimentoRepository.findAllByPacienteId(pacienteId);
    }

    @Transactional(readOnly = true)
    public List<PacienteEstabelecimento> vinculosDoPaciente(UUID pacienteId) {
        return vinculoRepository.findAllById_PacienteId(pacienteId);
    }

    private void exigirPacienteEClinica(UUID pacienteId, UUID estabelecimentoId) {
        if (!pacienteRepository.existsById(pacienteId)) {
            throw new RecursoNaoEncontradoException("Paciente não encontrado: " + pacienteId);
        }
        if (!estabelecimentoRepository.existsById(estabelecimentoId)) {
            throw new RecursoNaoEncontradoException("Estabelecimento não encontrado: " + estabelecimentoId);
        }
    }

    static String normalizarTelefone(String telefone) {
        return telefone.replaceAll("\\D", "");
    }
}
