# ADR 0001 — Qual lock, decidido pela contenção medida

Status: aceito · 2026-09-04 · supera: —

## Contexto

A recomendação que se repete em todo lugar é "prefira lock otimista; pessimista só quando a
contenção for alta". Ela está certa — e é inútil sem um número, porque ninguém define o que
é alta.

Este projeto mediu. O cenário é o pior possível de propósito: **uma linha só**, 200
tentativas concorrentes, 24 threads, 50 unidades em estoque.

## O que foi medido

```
estrategia   reservadas  sem estoque  por concorrencia  retentativas    tempo
otimista             50          119                31           266   1260 ms
pessimista           50          150                 0             0    420 ms
```

O pessimista foi **três vezes mais rápido** e não perdeu nenhuma requisição para erro
técnico.

Isso contraria a intuição comum, e a razão é direta: sob contenção alta na mesma linha, o
lock otimista não elimina a espera — ele a substitui por **trabalho jogado fora**. Foram 266
transações abertas, executadas e descartadas, cada uma com sua ida ao banco. O pessimista
faz fila e cada requisição trabalha uma vez só.

O detalhe que mais importa está na coluna "por concorrência": **31 clientes receberam erro
técnico** — "tente de novo" — quando a resposta verdadeira era "acabou". Aqui isso não
custou venda (as 50 unidades foram vendidas nos dois casos), mas se houvesse estoque
sobrando seriam 31 vendas perdidas por disputa.

## Por que o otimista continua sendo o padrão

O cenário medido é o extremo. Num catálogo real, a contenção por SKU é quase sempre baixa:
dois pedidos do mesmo produto no mesmo milissegundo são exceção, não regra. Com contenção
baixa:

- o otimista não segura lock nenhum, e nenhuma transação espera por outra;
- **nenhuma conexão fica presa esperando** — que é o custo escondido do pessimista;
- o conflito é raro, e o retry raro é barato.

O lock pessimista, sob contenção baixa, paga o custo de segurar linha e conexão sem ganhar
nada em troca.

## Decisão

**Lock otimista por padrão. Lock pessimista nas linhas quentes conhecidas.**

O número que decide, e que este projeto ensina a medir: **a taxa de conflito**. O contador
`retentativas()` existe para isso.

- abaixo de ~1% das tentativas em conflito: otimista, sem discussão;
- acima de ~10%: o retry vira parte do problema, e o pessimista provavelmente ganha;
- entre os dois: medir, com o tráfego real, e decidir com o número na mão.

Neste projeto, com 200 tentativas e 266 retentativas, a taxa passou de **100%** — cada
tentativa conflitou mais de uma vez em média. É o território onde o pessimista ganha, e o
teste mostra ganhando.

## O custo do pessimista que não aparece nesta tabela

A conexão fica presa durante toda a espera pelo lock. Com o pool esgotado, **requisições que
nem tocam neste produto passam a falhar** — o efeito se espalha para o sistema inteiro.

Esse é o argumento mais forte contra o pessimista, e ele **não foi medido aqui**: a
comparação acima usa um pool de 10 conexões e não isola o efeito. Está registrado como
exercício no README, e a honestidade de dizer que não foi medido vale mais que uma
afirmação sem número.

## Consequências

- \+ A escolha tem um critério objetivo — taxa de conflito — em vez de preferência.
- \+ O contador de retentativas é a instrumentação que permite revisar a decisão com dados.
- − Duas estratégias no código significam duas coisas para manter, e a possibilidade de
  alguém usar a errada. Mitigado por os nomes das classes dizerem qual é qual.
- − A régua de 1% e 10% é experiência, e não lei. O valor certo depende de quanto custa uma
  venda perdida e de quanto custa uma conexão presa.
- − Nenhuma das duas resolve contenção extrema de verdade. Para uma linha realmente quente —
  ingresso de show, lançamento de produto — a saída sai do banco: fila serializada por SKU,
  ou reserva em memória com liquidação assíncrona.
