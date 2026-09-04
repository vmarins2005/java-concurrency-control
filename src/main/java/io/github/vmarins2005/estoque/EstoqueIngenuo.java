package io.github.vmarins2005.estoque;

import org.springframework.stereotype.Service;

/**
 * A versao errada, mantida no repositorio de proposito.
 *
 * <p>Ela nao tem defeito visivel: passa em qualquer teste de uma thread so, e o codigo
 * parece obviamente correto. O defeito e a janela entre ler a quantidade e grava-la - duas
 * requisicoes que caiam dentro dela leem o mesmo numero e as duas se acham autorizadas.
 *
 * <p>{@code VendaDuplicadaTest} prova, rodando: uma unidade em estoque, oito reservas
 * confirmadas.
 */
@Service
public class EstoqueIngenuo implements Estoque {

    private final TransacaoDeReserva transacao;

    EstoqueIngenuo(TransacaoDeReserva transacao) {
        this.transacao = transacao;
    }

    @Override
    public String nome() {
        return "ingenuo";
    }

    @Override
    public void reservar(String sku, String clienteId, int quantidade, Runnable janelaDeCorrida) {
        transacao.semProtecao(sku, clienteId, quantidade, janelaDeCorrida);
    }
}
