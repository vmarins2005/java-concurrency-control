package io.github.vmarins2005.estoque;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A mesma disputa do {@code VendaDuplicadaTest}, agora com {@code @Version}.
 *
 * <p>O mesmo ponto de encontro, a mesma janela, o mesmo numero de threads. So a estrategia
 * muda - e o resultado deixa de ser venda duplicada.
 */
class LockOtimistaTest extends BancoDeTestes {

    private static final String SKU = "ULTIMA-UNIDADE";
    private static final int CONCORRENTES = 8;

    @Autowired
    private EstoqueComLockOtimista otimista;

    @BeforeEach
    void zerarContador() {
        otimista.zerarContador();
    }

    @Test
    @DisplayName("oito clientes disputam a ultima unidade e apenas um leva")
    void apenasUmLeva() throws Exception {
        cadastrarProduto(SKU, 1);
        JanelaDeCorrida janela = new JanelaDeCorrida(CONCORRENTES);

        List<Resultado> resultados = emParalelo(CONCORRENTES, tarefas(janela, 1));

        assertThat(resultados.stream().filter(Resultado::sucesso).count()).isEqualTo(1);
        assertThat(reservas.totalReservadoDe(SKU)).isEqualTo(1);
        assertThat(quantidadeEmEstoque(SKU)).isZero();

        // Os sete que perderam a corrida tentaram de novo, releram o estoque ja zerado e
        // terminaram em "sem estoque" - que e a resposta correta, e nao um erro tecnico.
        assertThat(resultados.stream().filter(resultado -> resultado.falhouCom(SemEstoque.class)).count())
                .isEqualTo(CONCORRENTES - 1);
        assertThat(otimista.retentativas())
                .as("cada perdedor conflitou ao menos uma vez")
                .isGreaterThanOrEqualTo(CONCORRENTES - 1L);
    }

    @Test
    @DisplayName("a invariante vale: reservado + disponivel continua sendo o estoque inicial")
    void invarianteMantida() throws Exception {
        cadastrarProduto(SKU, 3);
        JanelaDeCorrida janela = new JanelaDeCorrida(CONCORRENTES);

        emParalelo(CONCORRENTES, tarefas(janela, 1));

        assertThat(reservas.totalReservadoDe(SKU) + quantidadeEmEstoque(SKU)).isEqualTo(3L);
        assertThat(quantidadeEmEstoque(SKU)).isNotNegative();
    }

    private List<Callable<Void>> tarefas(JanelaDeCorrida janela, int quantidade) {
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < CONCORRENTES; i++) {
            String cliente = "CLI-" + i;
            tarefas.add(() -> {
                otimista.reservar(SKU, cliente, quantidade, janela);
                return null;
            });
        }
        return tarefas;
    }
}
