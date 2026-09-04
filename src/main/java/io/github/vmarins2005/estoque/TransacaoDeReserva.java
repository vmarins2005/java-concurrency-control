package io.github.vmarins2005.estoque;

import java.time.Clock;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * As transacoes de reserva, num bean separado.
 *
 * <p>Separado porque {@code @Transactional} em metodo chamado de dentro da mesma classe nao
 * passa pelo proxy do Spring e nao abre transacao. E aqui isso seria fatal de um jeito
 * especifico: o retry do lock otimista precisa que <b>cada tentativa seja uma transacao
 * nova</b>. Repetir dentro da mesma transacao ja invalidada nao adianta nada - o contexto
 * de persistencia continua sujo, e a segunda tentativa falha igual.
 */
@Component
class TransacaoDeReserva {

    private final RepositorioDeProdutos produtos;
    private final RepositorioDeReservas reservas;
    private final Clock relogio;

    TransacaoDeReserva(RepositorioDeProdutos produtos, RepositorioDeReservas reservas, Clock relogio) {
        this.produtos = produtos;
        this.reservas = reservas;
        this.relogio = relogio;
    }

    /**
     * Ler, conferir, gravar - sem nenhuma protecao. E o codigo que quase todo mundo escreve
     * na primeira vez, e ele esta errado de um jeito que so aparece sob concorrencia.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void semProtecao(String sku, String clienteId, int quantidade, Runnable janelaDeCorrida) {
        Integer disponivel = produtos.quantidadeAtual(sku);
        if (disponivel == null) {
            throw new NoSuchElementException("produto nao encontrado: " + sku);
        }

        janelaDeCorrida.run();

        if (disponivel < quantidade) {
            throw new SemEstoque(sku, disponivel, quantidade);
        }
        produtos.gravarQuantidade(sku, disponivel - quantidade);
        reservas.save(new Reserva(sku, clienteId, quantidade, relogio.instant()));
    }

    /**
     * Carrega a entidade gerenciada: o {@code @Version} entra em acao no flush, e quem
     * perder a corrida recebe excecao em vez de sobrescrever o trabalho do outro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void comLockOtimista(String sku, String clienteId, int quantidade, Runnable janelaDeCorrida) {
        Produto produto = produtos.findById(sku)
                .orElseThrow(() -> new NoSuchElementException("produto nao encontrado: " + sku));

        janelaDeCorrida.run();

        produto.reservar(quantidade);
        reservas.save(new Reserva(sku, clienteId, quantidade, relogio.instant()));
    }

    /**
     * {@code SELECT ... FOR UPDATE}: a linha fica travada ate o commit, e quem chegar depois
     * espera a vez. Nao ha retry porque nao ha conflito - ha fila.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void comLockPessimista(String sku, String clienteId, int quantidade, Runnable janelaDeCorrida) {
        Produto produto = produtos.buscarParaAtualizar(sku)
                .orElseThrow(() -> new NoSuchElementException("produto nao encontrado: " + sku));

        janelaDeCorrida.run();

        produto.reservar(quantidade);
        reservas.save(new Reserva(sku, clienteId, quantidade, relogio.instant()));
    }
}
