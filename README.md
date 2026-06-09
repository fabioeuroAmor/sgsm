# SGSM — Sistema de Gerenciamento de Serviços Médicos

Microserviço REST construído com **Spring Boot 4** e **Java 21** para gerenciar médicos, estabelecimentos, pacientes, serviços médicos, agendas e agendamentos — incluindo o fluxo de atendimento domiciliar.

---

## Tecnologias

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Data JPA | (Boot Managed) |
| PostgreSQL | 18 |
| Lombok | (Boot Managed) |
| ModelMapper | 2.4.4 |
| Gson | 2.13.1 |
| Jackson Databind | 2.15.2 |
| SpringDoc OpenAPI (Swagger) | 2.8.6 |

---

## Pré-requisitos

- **JDK 21** instalado e no `PATH`
- **PostgreSQL 18** rodando em `localhost:5432`
- Banco de dados `postgres` acessível com usuário `postgres` / senha `postgres`

---

## Configuração

`src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres?stringtype=unspecified
    username: postgres
    password: postgres
  jpa:
    show-sql: true
```

Altere as credenciais conforme o seu ambiente.

---

## Como executar

```bash
# Build e execução
./mvnw spring-boot:run

# Somente build
./mvnw clean package

# Executar o JAR gerado
java -jar target/sgsm-0.0.1-SNAPSHOT.jar
```

A API fica disponível em `http://localhost:8080`.

Documentação interativa (Swagger UI): `http://localhost:8080/swagger-ui.html`

---

## Estrutura do projeto

```
src/main/java/br/com/sgsm/
├── controller/       # Endpoints REST
├── service/          # Regras de negócio
├── domain/           # Entidades JPA e enums
├── dto/              # Requests e Responses
├── repository/       # Interfaces Spring Data JPA
├── exception/        # Handler global de erros
└── config/           # ModelMapper
```

---

## Domínios e Endpoints

Base path: `/v1/api`

### Médicos — `/v1/api/medicos`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/medicos` | Cadastrar médico |
| `GET` | `/medicos` | Listar (`?ativo=true&especialidade=`) |
| `GET` | `/medicos/{id}` | Consultar médico |
| `PUT` | `/medicos/{id}` | Atualizar médico |
| `DELETE` | `/medicos/{id}` | Inativar médico |

### Estabelecimentos — `/v1/api/estabelecimentos`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/estabelecimentos` | Cadastrar estabelecimento |
| `GET` | `/estabelecimentos` | Listar (`?ativo=&uf=&cidade=`) |
| `GET` | `/estabelecimentos/{id}` | Consultar estabelecimento |
| `PUT` | `/estabelecimentos/{id}` | Atualizar estabelecimento |
| `DELETE` | `/estabelecimentos/{id}` | Inativar estabelecimento |
| `GET` | `/estabelecimentos/{id}/medicos` | Listar médicos do estabelecimento |
| `PUT` | `/estabelecimentos/{id}/medicos` | Associar médicos ao estabelecimento |

### Pacientes — `/v1/api/pacientes`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/pacientes` | Cadastrar paciente |
| `GET` | `/pacientes` | Listar (`?ativo=true`) |
| `GET` | `/pacientes/{id}` | Consultar paciente |
| `PUT` | `/pacientes/{id}` | Atualizar paciente |
| `DELETE` | `/pacientes/{id}` | Inativar paciente |

### Serviços Médicos — `/v1/api/servicos`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/servicos` | Cadastrar serviço |
| `GET` | `/servicos` | Listar (`?ativo=&medicoId=`) |
| `GET` | `/servicos/{id}` | Consultar serviço |
| `PUT` | `/servicos/{id}` | Atualizar serviço |
| `DELETE` | `/servicos/{id}` | Inativar serviço |

### Agenda Médico — `/v1/api/agenda-medico`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/agenda-medico` | Cadastrar horário de atendimento |
| `GET` | `/agenda-medico?medicoId=` | Listar agenda do médico |
| `DELETE` | `/agenda-medico/{id}` | Remover horário |

### Agendamentos — `/v1/api/agendamentos`

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/agendamentos` | Criar agendamento |
| `GET` | `/agendamentos` | Listar (`?pacienteId=&medicoId=&status=`) |
| `GET` | `/agendamentos/{id}` | Consultar agendamento |
| `GET` | `/agendamentos/slots` | Listar slots disponíveis |
| `GET` | `/agendamentos/medico/{id}/estabelecimentos` | Estabelecimentos onde o médico atende |
| `PATCH` | `/agendamentos/{id}/status` | Atualizar status |
| `PATCH` | `/agendamentos/{id}/cancelar` | Cancelar agendamento |

---

## Fluxo de Agendamento

### Tipos de atendimento (`TipoAgendamento`)

| Valor | Descrição |
|---|---|
| `PRESENCIAL` | Paciente vai ao estabelecimento |
| `DOMICILIAR` | Médico se desloca até o paciente |
| `TELEMEDICINA` | Consulta remota |

### Ciclo de vida do status (`StatusAgendamento`)

```
PENDENTE
  └─► AGUARDANDO_PAGAMENTO
  └─► CONFIRMADO
        └─► EM_ANDAMENTO          (presencial / telemedicina)
        └─► A_CAMINHO             (domiciliar apenas)
              └─► CHEGOU
                    └─► EM_ANDAMENTO
                          └─► CONCLUIDO
CANCELADO   (qualquer status, exceto CONCLUIDO)
NO_SHOW
```

### Slots disponíveis

`GET /v1/api/agendamentos/slots?medicoId={uuid}&data={yyyy-MM-dd}[&estabelecimentoId={uuid}]`

- Se `estabelecimentoId` for omitido, retorna slots das **agendas domiciliares** do médico.
- Exclui automaticamente slots com bloqueios de agenda e horários já ocupados.
- Para atendimentos domiciliares, o intervalo entre slots considera o `intervaloDeslocamentoMinutos` cadastrado na agenda.

---

## Tratamento de erros

Todas as respostas de erro seguem o formato:

```json
{
  "timestamp": "2026-06-09T12:00:00Z",
  "status": 404,
  "erro": "Recurso não encontrado: ..."
}
```

| HTTP | Situação |
|---|---|
| `400` | Argumento inválido (ex: transição de status inválida) |
| `404` | Recurso não encontrado |
| `500` | Erro interno |

---

## Modelos principais

### `CadastrarAgendaMedicoRequest`

```json
{
  "medicoId": "uuid",
  "estabelecimentoId": "uuid",        // null para domiciliar
  "diaSemana": "SEGUNDA",
  "horaInicio": "08:00",
  "horaFim": "18:00",
  "duracaoSlotMinutos": 30,
  "dataVigenciaInicio": "2026-01-01",
  "dataVigenciaFim": "2026-12-31",    // null = sem fim
  "domiciliar": true,
  "intervaloDeslocamentoMinutos": 30, // tempo de deslocamento entre pacientes
  "raioKm": 20.0,
  "cidadeAtendimento": "São Paulo",
  "ufAtendimento": "SP"
}
```

### `CadastrarAgendamentoRequest`

```json
{
  "pacienteId": "uuid",
  "servicoMedicoId": "uuid",
  "estabelecimentoId": "uuid",  // omitir para domiciliar
  "tipo": "PRESENCIAL",
  "dataHoraInicio": "2026-06-14T09:00:00-03:00",
  "observacoes": "Texto opcional"
}
```

### `AtualizarStatusAgendamentoRequest`

```json
{
  "status": "A_CAMINHO",
  "localizacaoMedico": "https://maps.app.goo.gl/..."  // obrigatório para A_CAMINHO
}
```
## Uso do logging via Logback

| Configuração                    | O que será mostrado                     |
|---------------------------------|-----------------------------------------|
| `<root level="INFO">`           | INFO, WARN, ERROR                      |
| `<root level="DEBUG">`          | DEBUG, INFO, WARN, ERROR              |
| `<root level="WARN">`           | WARN, ERROR                          |

## Tipos de tets unitarios a serem implementados
Tests de camada de Service e de Controller

## Relatório de Cobertura de Testes com jacoco
Após executar os testes, o relatório de cobertura está disponível em:

## plaintext
sboot-atomico-rag/target/site/jacoco/index.html

## Relatório de Cobertura de Verificação de Vunerabilidades de libs com Maven OWASP Dependency-Check
sboot-atomico-rag/target/dependency-check-report.html

## Banco de dados do OWASP Dependency-Check
https://raw.githubusercontent.com/Retirejs/retire.js/master/repository/jsrepository.json

## Verificar hierarquias de dependencias do projeto. Executar o comando abaixo no gitbash
mvn dependency:tree -DoutputFile=dependencias.txt -Dverbose

## Como Rodar o Projeto
## Antes de rodar os comandos docker. Dei o (sudo su) no Linux e digite sua senha de administrador
sudo su
## Construir a imagem Docker: No terminal, na pasta do seu projeto, execute:
docker build -t minha-microservice .

## Rodar sua aplicação em um container:
docker run -p 8080:8080 minha-microservice

### Você rodar sua aplicação, chamando a classe main() de seu projeto via IDE e em modo debug.

### Clonar o repositório

```bash
git clone https://XXXXXXXXXXXX
cd nome-do-repo
