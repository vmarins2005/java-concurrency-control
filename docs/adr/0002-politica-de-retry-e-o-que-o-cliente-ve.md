# ADR 0002 — Política de retry, e o que o cliente vê quando ele esgota

Status: aceito · 2026-09-04 · supera: —

## Contexto

Lock otimista sem retry é inútil: o primeiro conflito vira erro para o cliente, e conflito é
o comportamento esperado, não a exceção. Com retry, três decisões aparecem.

## 1. Onde o retry acontece

**Fora da transação.** Cada tentativa abre uma transação nova.

Não é detalhe de implementação: repetir dentro da transação já invalidada não funciona. O
contexto de persistência continua sujo, a entidade continua com a versão velha, e a segunda
tentativa falha igual à primeira.

É por isso que `TransacaoDeReserva` é um bean separado — `@Transactional` chamado de dentro
da mesma classe não passa pelo proxy, e aqui isso quebraria o retry de um jeito silencioso.

## 2. Quantas tentativas, e com que espera

Cinco tentativas, com **backoff exponencial e jitter**, teto de 50 ms.

O jitter não é refinamento: sem ele, as tentativas que colidiram voltam todas no mesmo
instante e colidem de novo. **O próprio retry vira a causa da contenção** — o efeito manada
que transforma um pico de disputa em uma sequência de picos.

Cinco é um chute educado. O número certo sai da contenção medida: com pouca disputa, a
segunda tentativa quase sempre resolve; com muita, aumentar o teto só faz o cliente esperar
mais para receber o mesmo erro. O ADR 0001 traz os números que este projeto mediu.

## 3. O que o cliente vê quando esgota

Esta é a decisão que sai do código e vira produto.

Existem **duas falhas diferentes**, e confundi-las é erro de produto:

| Falha | O que aconteceu | O que o cliente deveria ver |
| --- | --- | --- |
| `SemEstoque` | acabou de verdade | "esgotado" — e talvez "avise-me quando voltar" |
| `ConcorrenciaExcessiva` | há estoque, mas a disputa venceu o retry | "tente de novo" |

São ações diferentes: "acabou" pede outra decisão do cliente; "tente de novo" pede a mesma
ação de novo. Uma API que devolve o mesmo erro para os dois casos faz o cliente desistir de
uma compra que ainda era possível.

Por isso as duas exceções são classes separadas, e não uma exceção genérica com mensagem
diferente. A borda mapeia por tipo:

- `SemEstoque` → **409 Conflict**, com o motivo de negócio;
- `ConcorrenciaExcessiva` → **503 Service Unavailable** com `Retry-After`, que é o código que
  diz "o problema é meu, não seu, e vale tentar de novo".

Na medição do ADR 0001, **31 clientes caíram no segundo caso**. Num sistema que devolvesse
"esgotado" para eles, seriam 31 pessoas informadas de algo falso.

## Consequências

- \+ O cliente recebe a informação certa, e a API distingue erro de negócio de erro técnico.
- \+ O retry é invisível no caminho feliz e barato no conflito raro.
- − Retry esconde contenção. Um sistema com muito retry parece saudável até o dia em que o
  esgotamento aparece. Por isso o contador de retentativas é exposto: **retry sem métrica é
  um problema adiado**.
- − Cinco tentativas com backoff somam até ~150 ms de latência antes de o cliente receber a
  má notícia. Sob disputa alta, esse tempo é gasto para chegar ao mesmo lugar.
- − A operação precisa ser idempotente para o retry ser seguro. Aqui é, porque a reserva só
  é gravada na tentativa que comita. Se houvesse chamada externa antes do commit, o retry
  duplicaria o efeito — e a estratégia teria de mudar.
