# ADR 0003 — Nível de isolamento escolhido

Status: aceito · 2026-09-04 · supera: —

## Contexto

Antes de escolher lock, vale entender o que o isolamento sozinho resolve — e o que não
resolve. A venda duplicada deste projeto acontece em `READ COMMITTED`, que é o padrão do
Postgres e o padrão da maioria dos sistemas.

O motivo é que `READ COMMITTED` **não promete nada sobre ler e depois escrever**. Ele
garante que você não lê dado não comitado; não garante que o dado lido continue valendo até
você gravar. A janela entre o `SELECT` e o `UPDATE` é exatamente onde a venda duplicada mora.

## Alternativas

**1. `READ COMMITTED` + controle explícito de concorrência. — escolhida**
O padrão, com `@Version` ou `SELECT ... FOR UPDATE` onde a regra exige.

**2. `REPEATABLE READ`.**
No Postgres, garante que a transação enxerga o mesmo instantâneo do início ao fim, e
**aborta** com erro de serialização quando duas transações tentam atualizar a mesma linha.

Resolve a venda duplicada. Mas o efeito é praticamente o do lock otimista, com duas
diferenças que pioram: o erro chega como `SQLException` de serialização em vez de uma
exceção de domínio, e ele passa a valer para a transação **inteira**, inclusive para tabelas
onde não havia disputa nenhuma. O retry precisa reexecutar tudo.

**3. `SERIALIZABLE`.**
A garantia mais forte: o resultado é sempre equivalente a alguma execução sequencial. No
Postgres é implementado com detecção de conflito, não com locks — e portanto também aborta
transações, com retry obrigatório.

É a escolha certa quando as invariantes envolvem **várias linhas** e não dá para travar cada
uma: "a soma dos limites de crédito desta carteira não pode passar de X". Aqui a invariante é
de uma linha só, e `SERIALIZABLE` cobraria abortos no sistema inteiro para resolver um
problema local.

## Decisão

`READ COMMITTED`, o padrão, com o controle de concorrência **explícito no código onde a
regra exige**.

A razão é de manutenção, não de desempenho: com `@Version` na entidade e
`buscarParaAtualizar` no repositório, **a proteção está visível para quem lê o código**.
Com isolamento mais alto, a proteção fica numa configuração global que ninguém vê ao ler o
serviço — e que alguém pode baixar um dia, por outro motivo, sem perceber que quebrou uma
invariante de negócio a três camadas de distância.

A `CHECK (quantidade_disponivel >= 0)` no schema é a última linha de defesa. Ela não
substitui nada — com ela sozinha, a venda duplicada viraria erro de constraint no meio da
requisição de um cliente, em vez de saldo errado. É melhor, e continua sendo defeito.

## Consequências

- \+ A proteção é local e legível: quem abre `TransacaoDeReserva` vê qual estratégia está em
  uso.
- \+ Nenhuma transação do sistema paga por uma regra que vale para uma tabela.
- \+ O comportamento é o padrão do banco, sem surpresa para quem chega ao projeto depois.
- − `READ COMMITTED` não protege nada sozinho. Toda escrita que dependa de leitura anterior
  precisa de proteção explícita — e **esquecer é fácil**, porque o código errado parece
  certo e passa em qualquer teste de uma thread só.
- − Invariante que envolve várias linhas não é coberta por esta decisão. Se aparecer uma —
  "a soma das reservas de um cliente não pode passar do limite" — a conversa é outra, e
  `SERIALIZABLE` volta à mesa com um ADR próprio.
