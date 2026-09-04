# Reserva de estoque — a última unidade disputada

Projeto de estudo de **concorrência no dado**: uma unidade em estoque, muitos clientes ao
mesmo tempo, e um teste que prova a venda duplicada antes de qualquer correção.

Décimo de uma série em que cada repositório isola um conceito.

## Como rodar

Precisa de Docker (Postgres real — semântica de lock é o tipo de coisa que banco em memória
simula mal).

```bash
./mvnw test
```

7 testes, ~29 segundos.

## O problema, provado

`VendaDuplicadaTest` roda o código que quase todo mundo escreve na primeira vez — ler,
conferir, gravar — com **uma unidade** em estoque e oito clientes simultâneos:

```
confirmadas ............. 8
reservado ............... 8
estoque final ........... 0
reservado + disponivel .. 8   (deveria ser 1)
```

Oito clientes receberam confirmação de uma unidade só. **Nenhuma exceção foi lançada em
lugar nenhum.**

O motivo é a janela entre ler a quantidade e gravá-la: as oito leram `1`, as oito passaram
pela verificação, e as oito gravaram o mesmo valor absoluto.

E o segundo teste da classe mostra por que o defeito sobrevive tanto tempo: **sem
concorrência, o mesmo código passa em qualquer teste.** Ele parece obviamente correto.

## O resultado que contraria a intuição

A recomendação que se repete em todo lugar é "prefira otimista; pessimista só sob contenção
alta" — e ela é inútil sem um número.

Medido aqui, no pior cenário possível de propósito (**uma linha só**, 200 tentativas
concorrentes, 24 threads, 50 unidades):

```
estrategia   reservadas  sem estoque  por concorrencia  retentativas    tempo
otimista             50          119                31           266   1260 ms
pessimista           50          150                 0             0    420 ms
```

O **pessimista foi três vezes mais rápido** e não perdeu nenhuma requisição para erro
técnico.

A razão é direta: sob contenção alta na mesma linha, o lock otimista não elimina a espera —
ele a substitui por **trabalho jogado fora**. Foram 266 transações abertas, executadas e
descartadas. O pessimista faz fila, e cada requisição trabalha uma vez só.

**Isso não torna o otimista errado.** O cenário medido é o extremo; num catálogo real, dois
pedidos do mesmo SKU no mesmo milissegundo são exceção. Com contenção baixa, o otimista não
segura lock nem conexão, e o conflito raro é barato.

O que o projeto ensina não é qual escolher — é **qual número decide**: a taxa de conflito.
O contador `retentativas()` existe para isso, e o ADR 0001 traz a régua.

## As duas falhas que não podem ser confundidas

A coluna "por concorrência" da tabela vale uma seção. São **31 clientes que receberam erro
técnico** — "tente de novo" — quando a resposta verdadeira era "acabou".

| Falha | O que aconteceu | O que o cliente deve ver |
| --- | --- | --- |
| `SemEstoque` | acabou de verdade | esgotado — talvez "avise-me quando voltar" |
| `ConcorrenciaExcessiva` | há estoque, a disputa venceu o retry | tente de novo |

São ações diferentes: "acabou" pede outra decisão; "tente de novo" pede a mesma ação de
novo. Uma API que devolve o mesmo erro para os dois casos faz o cliente desistir de uma
compra que ainda era possível.

Por isso são classes de exceção separadas, e a borda mapeia por tipo: `409` para o primeiro,
`503` com `Retry-After` para o segundo.

## O ponto de encontro, e por que ele não serve para o pessimista

A corrida é forçada por um `CountDownLatch` que segura todas as threads entre a leitura e a
escrita. Sem isso, provocar a janela dependeria de sorte de agendamento — o teste passaria
numa máquina e falharia em outra.

Ele **não serve para o lock pessimista**, e a razão é instrutiva: lá o lock é adquirido
*antes* da janela existir, então só a primeira thread chegaria nela — as outras estão
bloqueadas no `SELECT ... FOR UPDATE`. O ponto de encontro nunca completaria.

Essa impossibilidade é exatamente a propriedade que o lock pessimista oferece.

## Decisões registradas

| ADR | Assunto |
| --- | --- |
| [0000](docs/adr/0000-decisoes-base-do-projeto.md) | Escopo, ponto de encontro, e por que a versão errada fica no repo |
| [0001](docs/adr/0001-qual-lock-decidido-pela-contencao-medida.md) | Qual lock, decidido pela contenção medida |
| [0002](docs/adr/0002-politica-de-retry-e-o-que-o-cliente-ve.md) | Política de retry, e o que o cliente vê |
| [0003](docs/adr/0003-nivel-de-isolamento-escolhido.md) | Nível de isolamento escolhido |

O ADR 0003 explica por que `READ COMMITTED` e não `SERIALIZABLE`: a proteção fica **visível
no código** em vez de escondida numa configuração global que alguém pode baixar um dia, por
outro motivo, sem perceber que quebrou uma invariante três camadas adiante.

## O que não foi medido

**O efeito do lock pessimista sobre o pool de conexões.** É o argumento mais forte contra
ele: a conexão fica presa durante toda a espera, e com o pool esgotado requisições que nem
tocam neste produto passam a falhar.

A comparação acima usa pool de 10 e **não isola esse efeito**. Dizer que não foi medido vale
mais que afirmar sem número — e virou o exercício 3.

## Exercícios

1. **Rode `VendaDuplicadaTest` sem o ponto de encontro.** Ele vai passar às vezes e falhar
   às vezes. É a diferença entre um teste de concorrência determinístico e um que dá sorte.
2. **Reduza a contenção**: 200 tentativas sobre 200 SKUs diferentes em vez de um só. A
   tabela se inverte, e você acabou de encontrar a fronteira que o ADR 0001 descreve.
3. **Meça o pool.** Rode a estratégia pessimista com `POOL=2` e com `POOL=20`, e adicione
   uma tarefa concorrente que só faz `SELECT 1` em outra tabela. Veja quando ela começa a
   falhar por timeout de conexão. É o custo escondido do pessimista, com número.
4. **Troque o isolamento para `SERIALIZABLE`** e rode a versão ingênua. Ela passa a
   funcionar — e agora responda: por que este projeto não escolheu isso?

## Regras de trabalho neste repositório

- Escrita que depende de leitura anterior precisa de proteção explícita. `READ COMMITTED`
  não protege nada sozinho, e o código errado parece certo.
- Retry sem métrica é problema adiado: o contador de retentativas é exposto de propósito.
- Erro de negócio e erro técnico são exceções diferentes, sempre.

## O que eu faria diferente

_A preencher depois de usar._
