# Test Plan — GET /v1/api/servicos-medicos/{id}

## Contexto

Verificação funcional ad-hoc do endpoint já existente `GET /v1/api/servicos-medicos/{id}`
(`ServicoMedicoController.consultar`), solicitada pelo usuário com um token JWT real
(perfil `MEDICO`, `roles: ["MEDICO"]`, `permissions: ["servico:read", ...]`). Não há
mudança de código associada — este é um QA de verificação do comportamento atual.

Ambiente: aplicação `sgsm` já rodando em `http://localhost:8080` (processo real, não
mock), Postgres 18 nativo em `localhost:5432` (schema `sgsm`, DB `postgres`), Redis em
`localhost:6379` (usado para blacklist de tokens revogados). Banco contém 1 registro
pré-existente em `sgsm.servico_medico`:
`241760a1-efdc-44c6-b3c2-1f3a64013fe4` (medicoId `ff9a2400-2217-48fa-ac91-b81a4032f2db`,
nome "Consulta de Rotina", ativo=true).

**Achados de leitura de código relevantes para o plano** (não são suposições, é o que o
código faz hoje):
- A rota `/v1/api/servicos-medicos/**` **não tem regra de autorização específica** em
  `SecurityConfig` (diferente de `/v1/api/servicos/**`, que exige `MEDICO`/`FUNCIONARIO`/
  `DESENVOLVEDOR`). Ela cai em `.anyRequest().authenticated()` — ou seja, **qualquer**
  perfil autenticado (inclusive `PACIENTE`) tem acesso de leitura, e o claim
  `permissions` do token **nunca é lido** por `JwtAuthFilter` (só `roles`, `perfil`,
  `referenciaId`, `email`). Isso será testado como item de negócio, não como bug óbvio.
- `JwtAuthFilter` trata token ausente, malformado, com assinatura inválida ou expirado
  **da mesma forma**: `chain.doFilter` sem popular o `SecurityContext`, delegando para o
  Spring Security decidir (sem `AuthenticationEntryPoint` customizado). Logo T04–T07
  devem produzir o mesmo status HTTP entre si.
- `remover()` (DELETE) faz soft-delete (`ativo=false`, sem deletar linha). Não existe
  endpoint para reverter isso (`AtualizarServicoMedicoRequest` não tem campo `ativo`).
  Por isso T03 usa um registro **descartável**, criado via `POST`, e não o registro
  pré-existente "Consulta de Rotina".
- `GlobalExceptionHandler` tem handler explícito só para `RecursoNaoEncontradoException`
  (404), `AcessoNegadoException` (403) e `IllegalArgumentException` (400); tudo mais cai
  no handler genérico `Exception.class` → 500 "erro-interno". Um `id` de path não-UUID
  gera `MethodArgumentTypeMismatchException`, que **não** é subtipo de
  `IllegalArgumentException` — suspeita de que caia no 500 genérico em vez de 400. Isso é
  o item T09, a testar ao vivo (não assumir o resultado).
- O `README.md` documenta o contrato de erro como `{"timestamp","status","erro"}`, mas o
  código usa `ProblemDetail` (RFC 7807: `type`, `title`, `status`, `detail`, `instance`).
  T02/T09 confirmam qual dos dois é o real.

## Tokens usados

- `TOKEN_ORIGINAL`: token fornecido pelo usuário na conversa (perfil MEDICO). Validade
  curta (~15 min a partir de 2026-08-15 12:11 UTC) — usado só para o smoke-check inicial
  de conectividade (já confirmado 200 na listagem antes deste plano).
- `TOKEN_VALIDO`: token gerado localmente com o mesmo `secret` de dev
  (`jwt.secret` em `application.yaml`, valor documentado como "não usar em produção"),
  mesmos claims do original (`perfil: MEDICO`, `roles: ["MEDICO"]`,
  `referenciaId: ff9a2400-2217-48fa-ac91-b81a4032f2db`), `exp` +1h a partir de agora —
  usado no restante do plano para não expirar no meio da execução.
- `TOKEN_EXPIRADO`: mesmos claims, `exp` no passado (assinado com o secret de dev real).
- `TOKEN_ASSINATURA_INVALIDA`: mesma estrutura do `TOKEN_VALIDO`, último caractere da
  assinatura corrompido de propósito.
- `TOKEN_SECRET_ERRADO`: claims válidos, assinado com um secret diferente do configurado
  na aplicação.

(Os três tokens sintéticos serão entregues ao subagente de execução como strings
literais, prontas para uso — não é necessário e não é permitido ao subagente gerar
tokens ou tocar em código/config.)

## Itens do plano

### A. Contrato de sucesso

- [x] **T01** — `GET /v1/api/servicos-medicos/241760a1-efdc-44c6-b3c2-1f3a64013fe4` com
  `TOKEN_VALIDO` → esperado `200`, corpo JSON com todos os campos de
  `ServicoMedicoResponse` (`id`, `medicoId`, `nome`, `descricao`, `preco`,
  `duracaoMinutos`, `domiciliar`, `taxaDeslocamento`, `ativo`, `criadoEm`,
  `atualizadoEm`), `id` do corpo igual ao da URL, `Content-Type: application/json`.
  **APROVADO** — ver `evidence/T01.md`.

### B. Não encontrado / soft-delete

- [x] **T02** — `GET` com um UUID sintaticamente válido mas inexistente na tabela
  (ex.: `00000000-0000-0000-0000-000000000000`) → esperado `404`, corpo no formato de
  erro real da aplicação (confirmar se é `ProblemDetail` ou o formato do README).
  **APROVADO** — ver `evidence/T02.md`. Confirmado: formato real é `ProblemDetail`
  (RFC 7807, `Content-Type: application/problem+json`), README está desatualizado.
- [x] **T03** — Fluxo soft-delete: `POST /v1/api/servicos-medicos` cria um registro
  descartável → `GET` do id criado confirma `200`/`ativo:true` → `DELETE` do mesmo id →
  `GET` de novo no mesmo id: esperado continuar `200` (não 404, não 410) com
  `ativo:false` no corpo, confirmando o soft-delete. Evidência inclui consulta direta ao
  Postgres (`SELECT ativo FROM sgsm.servico_medico WHERE id = ...`) antes e depois do
  DELETE. **APROVADO** — ver `evidence/T03.md`.

### C. Autenticação / Autorização

- [x] **T04** — `GET` sem header `Authorization` → registrar o status HTTP real
  (`401` ou `403`, a app não tem `AuthenticationEntryPoint` customizado).
  **APROVADO** — ver `evidence/T04.md`. Status real observado: `403`.
- [x] **T05** — `GET` com `TOKEN_EXPIRADO` → esperado o **mesmo status** de T04 (filtro
  trata expirado = anônimo). **APROVADO** — ver `evidence/T05.md`. Status: `403`, igual a T04.
- [x] **T06** — `GET` com `TOKEN_ASSINATURA_INVALIDA` → esperado o mesmo status de T04.
  **APROVADO** — ver `evidence/T06.md`. Status: `403`, igual a T04.
- [x] **T07** — `GET` com `TOKEN_SECRET_ERRADO` → esperado o mesmo status de T04.
  **APROVADO** — ver `evidence/T07.md`. Status: `403`, igual a T04.
- [x] **T08** — `GET` com header `Authorization` mal formado, sem prefixo `Bearer `
  (ex.: `Authorization: TOKEN_VALIDO` cru, ou `Authorization: Basic xxx`) → esperado o
  mesmo status de T04. **APROVADO** — ver `evidence/T08.md`. Status: `403` nos dois
  casos testados, igual a T04.
- [x] **T09-NEG** *(decisão de negócio, não é bug de código a corrigir nesta rodada)* —
  Confirmar com evidência que `TOKEN_VALIDO` (perfil MEDICO) acessa o endpoint mesmo sem
  a rota exigir role específica em `SecurityConfig`. Registrar como nota para o
  responsável decidir se isso é intencional (endpoint de leitura pública entre perfis
  autenticados) — **não testável nesta rodada para outros perfis** (PACIENTE,
  FUNCIONARIO) por não haver token desses perfis disponível na conversa.
  **APROVADO (para MEDICO) / NÃO TESTÁVEL (para outros perfis, por falta de token)** —
  ver `evidence/T09.md`. Fica pendente decisão do responsável sobre se o acesso aberto a
  qualquer perfil autenticado é intencional.

### D. Edge cases do path variable `{id}`

- [x] **T10** — `GET` com `id` não-UUID (ex.: `/v1/api/servicos-medicos/abc-123`) →
  registrar o status HTTP real observado (esperar 400 pelo contrato documentado, mas
  suspeita fundamentada em código de que cai em 500 — confirmar ao vivo, sem presumir).
  Reportar como achado se vier 500. **APROVADO** — ver `evidence/T10.md`. Status real:
  `400` (não 500 — a suspeita de código não se confirmou; Spring converteu
  automaticamente, mas o corpo não segue o formato customizado de erro da aplicação).
- [x] **T11** — `GET /v1/api/servicos-medicos/` (id vazio, barra final) → confirmar se
  cai na rota de listagem (`GET /v1/api/servicos-medicos`, retornando lista) ou em erro.
  **APROVADO** — ver `evidence/T11.md`. Cai em erro `404` ("No static resource"), não na
  listagem.
- [x] **T12** — `GET` com `id` truncado (UUID com um caractere a menos) → registrar
  status (mesma suspeita de T10). **APROVADO** — ver `evidence/T12.md`. Status real:
  `404` (`UUID.fromString` é permissivo e interpretou como outro UUID válido, mas
  inexistente na tabela — não houve 500).
- [x] **T13** — `GET` com `id` contendo espaço/URL-encoded extra
  (ex.: `.../241760a1-efdc-44c6-b3c2-1f3a64013fe4%20`) → registrar status.
  **APROVADO** — ver `evidence/T13.md`. Status: `200`, espaço à direita ignorado.

### E. Headers

- [x] **T14** — `GET` válido (T01) mas com `Content-Type: text/plain` no request (GET
  sem corpo) → confirmar que não afeta o resultado (deve continuar 200). **APROVADO** —
  ver `evidence/T14.md`.
- [x] **T15** — `GET` válido com `Accept: application/xml` → registrar se a app ignora e
  devolve JSON (200) ou responde `406 Not Acceptable`. **APROVADO** — ver
  `evidence/T15.md`. Resultado real: `406 Not Acceptable` (não ignora, recusa).

### F. Idempotência / Concorrência

- [x] **T16** — Repetir o `GET` de T01 3x seguidas → confirmar resposta idêntica
  (idempotência natural de leitura). **APROVADO** — ver `evidence/T16.md`.
- [x] **T17** — Disparar 5 `GET`s simultâneos no mesmo id (T01) → confirmar que todos
  retornam `200` com corpo consistente, sem erro de concorrência (leitura pura, sem
  lock, risco baixo mas a verificar). **APROVADO** — ver `evidence/T17.md`.

### G. Observabilidade

- [ ] **T18** — Durante a execução de T01, capturar uma linha do log da aplicação
  (nível `DEBUG` para `br.com.sgsm` conforme `application.yaml`) mostrando o SQL/consulta
  disparada pela requisição — evidência de que a chamada gerou rastro observável.
  **NÃO TESTÁVEL NESTA RODADA** — ver `evidence/T18.md`. Motivo: não há appender de
  arquivo configurado (log só vai para console); o processo real da aplicação (PID 3980,
  `br.com.sgsm.SgsmApplication`) foi iniciado por uma run configuration do IntelliJ IDEA
  e seu console está anexado à janela "Run" da IDE, inacessível a este subagente headless
  (sem acesso a arquivo de log nem à UI da IDE). Nenhum log foi simulado ou inferido por
  leitura de código. Pendente de decisão do responsável (ex.: redirecionar stdout para
  arquivo e reexecutar este item).

### H. Não aplicável (justificativa por leitura de código, não por limitação de ambiente)

- **N/A — Timeout de dependência lenta / serviço externo fora do ar**: `consultar()` em
  `ServicoMedicoService` só chama `repository.findById` — não há chamada a serviço
  externo (diferente de `cadastrar`/`atualizar`, que publicam em `VetorizacaoPublisher`).
  Não há o que testar de timeout de dependência neste endpoint `GET` específico.
- **N/A — Rollback de transação em operação composta**: `consultar()` é
  `@Transactional(readOnly = true)` de uma única leitura; não há operação composta a
  testar rollback neste endpoint.
- **N/A — Paginação**: o endpoint retorna um único recurso por id, não uma coleção.
