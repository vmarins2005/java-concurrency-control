package io.github.vmarins2005.estoque;

import org.springframework.stereotype.Service;

/**
 * Lock pessimista: {@code SELECT ... FOR UPDATE}.
 *
 * <p>A transacao segura a linha ate comitar, e quem chegar depois espera. Nao ha conflito -
 * ha fila. Por isso nao existe retry aqui: ninguem perde a corrida, todo mundo e atendido
 * na vez.
 *
 * <p>O preco esta em outro lugar, e e o que menos se percebe: <b>a conexao fica presa
 * durante toda a espera</b>. Com o pool esgotado, requisicoes que nem tocam neste produto
 * passam a falhar. Ver ADR 0001.
 */
@Service
public class EstoqueComLockPessimista implements Estoque {

    private final TransacaoDeReserva transacao;

    EstoqueComLockPessimista(TransacaoDeReserva transacao) {
        this.transacao = transacao;
    }

    @Override
    public String nome() {
        return "pessimista";
    }

    @Override
    public void reservar(String sku, String clienteId, int quantidade, Runnable janelaDeCorrida) {
        transacao.comLockPessimista(sku, clienteId, quantidade, janelaDeCorrida);
    }
}
