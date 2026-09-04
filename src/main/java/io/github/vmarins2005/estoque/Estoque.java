package io.github.vmarins2005.estoque;

/**
 * As tres estrategias implementam a mesma operacao, para os testes poderem rodar a mesma
 * disputa contra cada uma e comparar o resultado.
 */
public interface Estoque {

    String nome();

    /**
     * @param janelaDeCorrida ponto de encontro executado <b>entre a leitura e a escrita</b>.
     *     Existe para o teste conseguir provocar a corrida de forma deterministica, em vez
     *     de depender de sorte de agendamento de thread.
     *     <p>Em producao a janela e menor - alguns milissegundos - e igualmente real: basta
     *     volume suficiente para que duas requisicoes caiam dentro dela.
     */
    void reservar(String sku, String clienteId, int quantidade, Runnable janelaDeCorrida);

    default void reservar(String sku, String clienteId, int quantidade) {
        reservar(sku, clienteId, quantidade, () -> { });
    }
}
