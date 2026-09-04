package io.github.vmarins2005.estoque;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A mesma disputa, com {@code SELECT ... FOR UPDATE}.
 *
 * <p>Sem ponto de encontro aqui, e a razao e a propria propriedade que se quer demonstrar:
 * o lock e adquirido antes da janela existir, entao so a primeira thread chegaria nela - as
 * outras estao bloqueadas no banco. Nao ha corrida para provocar.
 */
class LockPessimistaTest extends BancoDeTestes {

    private static final String SKU = "ULTIMA-UNIDADE";
    private static final int CONCORRENTES = 8;

    @Autowired
    private EstoqueComLockPessimista pessimista;

    @Test
    @DisplayName("oito clientes na fila, um leva e sete ouvem que acabou")
    void apenasUmLeva() throws Exception {
        cadastrarProduto(SKU, 1);

        List<Resultado> resultados = emParalelo(CONCORRENTES, tarefas(1));

        assertThat(resultados.stream().filter(Resultado::sucesso).count()).isEqualTo(1);
        assertThat(reservas.totalReservadoDe(SKU)).isEqualTo(1);
        assertThat(quantidadeEmEstoque(SKU)).isZero();

        // Ninguem recebeu erro tecnico: quem perdeu, perdeu por falta de estoque. Nao ha
        // conflito de versao porque nao ha corrida - ha fila.
        assertThat(resultados.stream().filter(resultado -> resultado.falhouCom(SemEstoque.class)).count())
                .isEqualTo(CONCORRENTES - 1);
    }

    @Test
    @DisplayName("com estoque para todos, a fila atende todos - nenhuma reserva se perde")
    void filaAtendeTodos() throws Exception {
        cadastrarProduto(SKU, CONCORRENTES);

        List<Resultado> resultados = emParalelo(CONCORRENTES, tarefas(1));

        assertThat(resultados.stream().filter(Resultado::sucesso).count()).isEqualTo(CONCORRENTES);
        assertThat(quantidadeEmEstoque(SKU)).isZero();
        assertThat(reservas.totalReservadoDe(SKU)).isEqualTo(CONCORRENTES);
    }

    private List<Callable<Void>> tarefas(int quantidade) {
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < CONCORRENTES; i++) {
            String cliente = "CLI-" + i;
            tarefas.add(() -> {
                pessimista.reservar(SKU, cliente, quantidade);
                return null;
            });
        }
        return tarefas;
    }
}
