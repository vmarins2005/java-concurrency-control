# ADR 0000 — Decisões base do projeto

Status: aceito · 2026-09-04 · supera: —

## Contexto

O projeto existe para transformar "concorrência no banco" de assunto teórico em número
medido. O critério é o do enunciado: **existe um teste que falha na versão ingênua e passa
na corrigida.**

## Decisões

### 1. A versão errada fica no repositório, e roda

`EstoqueIngenuo` não é código morto: `VendaDuplicadaTest` o executa e afirma que a venda
duplicada acontece — oito reservas para uma unidade em estoque.

Sem ver isso rodando, lock otimista e pessimista parecem cerimônia para um problema
teórico. É o mesmo padrão dos projetos de outbox e de rateio desta série: **provar o
problema antes de resolvê-lo.**

Para a versão ingênua conseguir ser ingênua, ela lê por consulta nativa e grava por
`UPDATE` de valor absoluto. Se carregasse a entidade gerenciada, o `@Version` entraria em
ação no flush e o defeito não apareceria — e o que se quer demonstrar é justamente o código
que ninguém pensou em proteger.

### 2. Ponto de encontro em vez de sorte

A corrida é forçada por um `CountDownLatch` que segura todas as threads entre a leitura e a
escrita.

Sem isso, provocar a janela dependeria de agendamento de thread: o teste passaria numa
máquina e falharia em outra — ou, pior, passaria por acidente exatamente quando o código
está errado.

O ponto de encontro **não serve para o lock pessimista**, e a razão é instrutiva: lá o lock
é adquirido antes da janela existir, então só a primeira thread chegaria nela. Essa
impossibilidade é exatamente a propriedade que o lock pessimista oferece.

### 3. Postgres de verdade

Semântica de lock é o tipo de coisa que banco em memória simula mal. `SELECT ... FOR UPDATE`
e o comportamento do `@Version` sob concorrência só valem alguma coisa medidos contra o
banco real — como o projeto de Testcontainers desta série documentou com casos concretos.

### 4. As falhas são o dado

O utilitário de execução paralela devolve o resultado de **cada** tarefa, inclusive as que
falharam, e com o tipo da exceção. Um teste de concorrência que só conta sucessos joga fora
metade da informação: a diferença entre falhar por `SemEstoque` e falhar por
`ConcorrenciaExcessiva` é o assunto do ADR 0002.

## Consequências

- A suíte roda em ~29 segundos, dos quais 22 são o teste de comparação sob carga.
- O repositório tem três implementações da mesma operação — uma errada e duas certas — e
  isso é proposital: é o que permite rodar a mesma disputa contra cada uma.
