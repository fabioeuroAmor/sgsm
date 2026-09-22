package br.com.sgsm.security;

import br.com.sgsm.exception.AcessoNegadoException;
import br.com.sgsm.repository.AdministradorEstabelecimentoRepository;
import br.com.sgsm.repository.FuncionarioRepository;
import br.com.sgsm.repository.MedicoEstabelecimentoRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Componente central de escopo por estabelecimento, compartilhado entre todos os
// services que precisam restringir MEDICO/FUNCIONARIO/ADMIN_ESTABELECIMENTO ao(s)
// proprio(s) estabelecimento(s) - evita duplicar a mesma logica em cada service
// (o padrao original, antes desta mudanca, so existia dentro de FuncionarioService,
// aplicado apenas a MEDICO).
@Component
public class EstabelecimentoAcessoService {

    private final ContextoSeguranca contextoSeguranca;
    private final MedicoEstabelecimentoRepository medicoEstabelecimentoRepository;
    private final AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository;
    private final FuncionarioRepository funcionarioRepository;

    public EstabelecimentoAcessoService(ContextoSeguranca contextoSeguranca,
                                        MedicoEstabelecimentoRepository medicoEstabelecimentoRepository,
                                        AdministradorEstabelecimentoRepository administradorEstabelecimentoRepository,
                                        FuncionarioRepository funcionarioRepository) {
        this.contextoSeguranca = contextoSeguranca;
        this.medicoEstabelecimentoRepository = medicoEstabelecimentoRepository;
        this.administradorEstabelecimentoRepository = administradorEstabelecimentoRepository;
        this.funcionarioRepository = funcionarioRepository;
    }

    // null = irrestrito (DESENVOLVEDOR e qualquer papel nao escopado por este
    // componente, ex.: PACIENTE - cujo escopo e por titularidade, nao estabelecimento).
    public List<UUID> estabelecimentosPermitidos() {
        if (contextoSeguranca.isMedico()) {
            return medicoEstabelecimentoRepository
                    .findById_MedicoIdAndAtivo(contextoSeguranca.getReferenciaId(), true)
                    .stream()
                    .map(me -> me.getId().getEstabelecimentoId())
                    .toList();
        }
        if (contextoSeguranca.isFuncionario()) {
            return funcionarioRepository.findById(contextoSeguranca.getReferenciaId())
                    .map(f -> List.of(f.getEstabelecimentoId()))
                    .orElse(List.of());
        }
        if (contextoSeguranca.isAdminEstabelecimento()) {
            return administradorEstabelecimentoRepository
                    .findById_AdministradorIdAndAtivo(contextoSeguranca.getReferenciaId(), true)
                    .stream()
                    .map(ae -> ae.getId().getEstabelecimentoId())
                    .toList();
        }
        return null;
    }

    public void validarEstabelecimentoPermitido(UUID estabelecimentoId) {
        List<UUID> permitidos = estabelecimentosPermitidos();
        if (permitidos == null) {
            return;
        }
        if (estabelecimentoId == null || !permitidos.contains(estabelecimentoId)) {
            throw new AcessoNegadoException("Acesso negado ao estabelecimento: " + estabelecimentoId);
        }
    }

    public boolean medicoPermitido(UUID medicoId) {
        List<UUID> permitidos = estabelecimentosPermitidos();
        if (permitidos == null) {
            return true;
        }
        if (permitidos.isEmpty()) {
            return false;
        }
        return medicoEstabelecimentoRepository.existsVinculoAtivo(medicoId, permitidos);
    }

    // Medicos vinculados a algum dos estabelecimentos permitidos - usado para escopar
    // visibilidade de paciente (via Agendamento.medicoId, nao Agendamento.estabelecimentoId,
    // que fica nulo em agendamento domiciliar). null = irrestrito.
    public List<UUID> medicosPermitidos() {
        List<UUID> permitidos = estabelecimentosPermitidos();
        if (permitidos == null) {
            return null;
        }
        if (permitidos.isEmpty()) {
            return List.of();
        }
        return medicoEstabelecimentoRepository
                .findById_EstabelecimentoIdInAndAtivo(permitidos, true)
                .stream()
                .map(me -> me.getId().getMedicoId())
                .distinct()
                .toList();
    }
}
