import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

// Teste de carga do fluxo principal do SGSM: login -> chamadas autenticadas
// em rotas de leitura frequentes. Rodar com os backends locais de pé:
//   ms-sboot-auth em :8081, sgsm em :8080
//
// Uso:
//   k6 run load-test/login-flow.js
//   k6 run --vus 20 --duration 1m load-test/login-flow.js   (carga customizada)
//   k6 run -e AUTH_URL=http://localhost:8081 -e API_URL=http://localhost:8080 load-test/login-flow.js

const AUTH_URL = __ENV.AUTH_URL || 'http://localhost:8081'
const API_URL = __ENV.API_URL || 'http://localhost:8080'
const EMAIL = __ENV.LOGIN_EMAIL || 'fabioeuro@gmail.com'
const SENHA = __ENV.LOGIN_SENHA || 'famor966'

const errorRate = new Rate('errors')
const loginDuration = new Trend('login_duration')

export const options = {
  scenarios: {
    carga_leve: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 5 },   // ramp-up suave
        { duration: '30s', target: 10 },  // carga leve sustentada
        { duration: '15s', target: 0 },   // ramp-down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],   // 95% das respostas abaixo de 500ms
    errors: ['rate<0.01'],              // menos de 1% de erro
  },
}

export default function () {
  let token

  group('login', () => {
    const res = http.post(
      `${AUTH_URL}/v1/api/auth/login`,
      JSON.stringify({ email: EMAIL, senha: SENHA }),
      { headers: { 'Content-Type': 'application/json' } }
    )
    loginDuration.add(res.timings.duration)

    const ok = check(res, {
      'login: status 200': (r) => r.status === 200,
      'login: recebeu accessToken': (r) => !!r.json('accessToken'),
    })
    errorRate.add(!ok)
    if (ok) token = res.json('accessToken')
  })

  if (!token) {
    sleep(1)
    return
  }

  const headers = { headers: { Authorization: `Bearer ${token}` } }

570,0101  group('rotas autenticadas', () => {
    const respostas = http.batch([02