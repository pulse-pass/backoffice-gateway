# backoffice-gateway

Gateway do backoffice do PulsePass: entrada única da aplicação do organizador e do administrador. Ele não tem regra de negócio nem banco de dados; recebe cada chamada, repassa para a API do serviço responsável e devolve a resposta do serviço sem alterá-la. Há um controlador e um proxy por serviço de destino, e os serviços chegam por rotas explícitas, sem Spring Cloud Gateway.

## Subir localmente

Para subir o gateway só é necessário o Docker: a imagem compila a aplicação com o JDK que ela mesma traz. O Java 21 só é necessário para rodar o `./mvnw` (lint e testes) fora do container.

```bash
./infrastructure/service-start.sh [test|development]
./infrastructure/service-stop.sh
```

O script cria a rede `pulse-pass` e o `infrastructure/.env` (a partir do `.env.example`) quando não existem, derruba o que estiver no ar e sobe o gateway. O modo padrão é `test`, em que os serviços de destino são atendidos pelo MockServer. No modo `development` o gateway aponta para o `onboarding-service` real, que precisa estar no ar na rede `pulse-pass`. Para cada par `<NAME>_TEST_URL` / `<NAME>_DEVELOPMENT_URL` do `.env`, o script exporta `<NAME>_URL` conforme o modo, então novos serviços de destino não exigem mudar o script. O `service-stop.sh` remove containers, volumes e imagens locais.

A API fica em `http://localhost:8090` (`API_PORT`) e, no modo `test`, o MockServer em `localhost:1090`. A origem liberada no CORS é a da aplicação do backoffice (`BACKOFFICE_APP_URL`, padrão `http://localhost:3000`).

## Rotas

`POST /legal_persons` (pública) é o autocadastro da empresa organizadora. O gateway repassa a chamada para `POST /onboarding/legal_persons` do `onboarding-service`: as rotas são expostas sem o prefixo do serviço, e o prefixo é acrescentado no repasse. A validação do token JWT das demais rotas ainda não existe nesta etapa.

O repasse é cru, sem esquema nem DTO. O corpo, a query string e os cabeçalhos `Content-Type` e `Accept-Language` seguem para o destino; `internal-token` e `Authorization` nunca seguem, e as rotas `/<serviço>/internal/**` não são expostas. A resposta volta igual à do serviço, com o mesmo status, o mesmo corpo e o mesmo `Content-Type`, qualquer que seja o status (201, 400, 409, 415, 500).

Erros produzidos pelo próprio gateway, no formato `{ "title", "message", "code", "extra_fields" }` (mensagem em inglês ou português, conforme `Accept-Language`):

| Código | Status | Situação |
| --- | --- | --- |
| `GEN00404` | 404 | Rota inexistente |
| `GEN00405` | 405 | Método não permitido na rota |
| `GEN00500` | 500 | Falha ao falar com o serviço de destino (conexão recusada, DNS, timeout) ou erro interno inesperado |

Os demais erros, como `GEN00400`, `GEN00415` e os códigos próprios de cada serviço (`ONB00001`, por exemplo), vêm do serviço de destino sem alteração. Os tempos limite do repasse são de 3 s para conectar e 30 s para ler a resposta.

A saúde do gateway fica em `/actuator/health`.

## Lint e testes

Os testes de integração dependem do MockServer, então o compose precisa estar no ar com `./infrastructure/service-start.sh test`.

```bash
./mvnw checkstyle:check        # Checkstyle
./mvnw test                    # testes unitários (relatório JaCoCo em target/site/jacoco)
./mvnw test -Pintegration      # testes de integração (exigem o compose no ar em modo test)
```
