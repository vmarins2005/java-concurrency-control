CREATE TABLE IF NOT EXISTS produto (
    sku                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    nome                  VARCHAR(200) NOT NULL,
    quantidade_disponivel INT          NOT NULL,
    versao                BIGINT       NOT NULL DEFAULT 0,

    -- A ultima linha de defesa. Se toda a logica de aplicacao falhar - e o teste da
    -- versao ingenua mostra que ela falha - o banco ainda recusa estoque negativo.
    --
    -- Nao substitui o controle de concorrencia: com ela sozinha, a venda duplicada vira
    -- um erro de constraint no meio de uma requisicao de cliente, em vez de um saldo
    -- errado. E melhor, e continua sendo defeito.
    CONSTRAINT ck_estoque_nao_negativo CHECK (quantidade_disponivel >= 0)
);

CREATE TABLE IF NOT EXISTS reserva (
    id         UUID        NOT NULL PRIMARY KEY,
    sku        VARCHAR(64) NOT NULL,
    cliente_id VARCHAR(64) NOT NULL,
    quantidade INT         NOT NULL,
    criada_em  TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_reserva_sku ON reserva (sku);
