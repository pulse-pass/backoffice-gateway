# backoffice-gateway

Gateway do backoffice do PulsePass: entrada única da aplicação do organizador e do administrador. Ele não tem regra de negócio nem banco de dados; recebe cada chamada, repassa para a API do serviço responsável e devolve a resposta do serviço sem alterá-la. Há um controlador e um proxy por serviço de destino, e os serviços chegam por rotas explícitas, sem Spring Cloud Gateway.

## Subir localmente

Para subir o gateway só é necessário o Docker: a imagem compila a aplicação com o JDK que ela mesma traz. O Java 21 só é necessário para rodar o `./mvnw` (lint e testes) fora do container.

```bash
./infrastructure/service-start.sh [test|development]
./infrastructure/service-stop.sh [test|development]
```

O script cria a rede `pulse-pass` e o `infrastructure/.env` (a partir do `.env.example`) quando não existem, derruba o ambiente do mesmo modo e sobe o gateway. O modo padrão é `test`, também no `service-stop.sh`. Para cada par `<NAME>_TEST_URL` / `<NAME>_DEVELOPMENT_URL` do `.env`, o script exporta `<NAME>_URL` conforme o modo, então novos serviços de destino não exigem mudar o script. Quem tem um `infrastructure/.env` criado antes desta organização deve apagá-lo, para que seja recriado a partir do `.env.example`.

Modo `test`: o gateway é autossuficiente e sobe a API e um MockServer no lugar dos serviços de destino, no projeto `backoffice-gateway-test`. A API fica em `http://localhost:8090` (`API_PORT`) e o MockServer em `localhost:1090`. É o modo usado pela CI e pelos testes de integração. O `service-stop.sh test` remove containers, volumes e imagens locais desse modo.

Modo `development`: sobe só a API (container `backoffice-gateway-api`, projeto `backoffice-gateway`), em `http://localhost:8190` (`DEVELOPMENT_API_PORT`), chamando os serviços reais pelo nome do container. O `onboarding-service` precisa estar de pé em modo `development` (`onboarding-service-api`). Como o gateway não tem banco, não há checagem da infraestrutura compartilhada. O `service-stop.sh development` derruba só esse modo.

Os dois modos podem rodar ao mesmo tempo. As portas 3306, 6379, 9094 e 8180 são da infraestrutura compartilhada. A origem liberada no CORS é a da aplicação do backoffice (`BACKOFFICE_APP_URL`, padrão `http://localhost:3000`).

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
