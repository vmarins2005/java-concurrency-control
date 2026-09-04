package io.github.vmarins2005.estoque;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Este teste <b>prova o problema</b>. Ele passa quando a venda duplicada acontece.
 *
 * <p>E o ponto de partida do projeto: sem ver oito reservas confirmadas para uma unidade em
 * estoque, lock otimista e pessimista parecem cerimônia para um problema teorico.
 */
class VendaDuplicadaTest extends BancoDeTestes {

    private static final String SKU = "ULTIMA-UNIDADE";
    private static final int CONCORRENTES = 8;

    @Autowired
    private EstoqueIngenuo ingenuo;

    @Test
    @DisplayName("uma unidade em estoque, oito clientes simultaneos, oito reservas confirmadas")
    void vendeOMesmoItemVariasVezes() throws Exception {
        cadastrarProduto(SKU, 1);
        JanelaDeCorrida janela = new JanelaDeCorrida(CONCORRENTES);

        List<Resultado> resultados = emParalelo(CONCORRENTES, tarefas(janela));

        long confirmadas = resultados.stream().filter(Resultado::sucesso).count();

        // Todas as oito passaram pela verificacao "tem estoque?" porque todas leram 1
        // antes de qualquer uma escrever.
        assertThat(confirmadas)
                .as("oito clientes receberam confirmacao de uma unidade so")
                .isEqualTo(CONCORRENTES);
        assertThat(reservas.totalReservadoDe(SKU)).isEqualTo(CONCORRENTES);

        // E o estoque desceu apenas uma vez: todas gravaram o mesmo valor absoluto.
        assertThat(quantidadeEmEstoque(SKU)).isZero();

        // A invariante que deveria valer sempre - reservado + disponivel = inicial - esta
        // quebrada, e nenhuma excecao foi lancada em nenhum lugar.
        assertThat(reservas.totalReservadoDe(SKU) + quantidadeEmEstoque(SKU))
                .as("reservado + disponivel deveria ser 1")
                .isNotEqualTo(1L);
    }

    @Test
    @DisplayName("sem concorrencia, o mesmo codigo passa em qualquer teste - e por isso o defeito sobrevive")
    void semConcorrenciaPareceCorreto() {
        cadastrarProduto(SKU, 1);

        ingenuo.reservar(SKU, "CLI-1", 1);

        assertThat(quantidadeEmEstoque(SKU)).isZero();
        assertThat(reservas.totalReservadoDe(SKU)).isEqualTo(1);
    }

    private List<Callable<Void>> tarefas(JanelaDeCorrida janela) {
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < CONCORRENTES; i++) {
            String cliente = "CLI-" + i;
            tarefas.add(() -> {
                ingenuo.reservar(SKU, cliente, 1, janela);
                return null;
            });
        }
        return tarefas;
    }
}
