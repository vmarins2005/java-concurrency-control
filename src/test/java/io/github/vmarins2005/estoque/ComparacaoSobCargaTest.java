package io.github.vmarins2005.estoque;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * As duas estrategias corretas, sob a mesma carga, com os numeros na tela.
 *
 * <p>Sem ponto de encontro: aqui a contencao e a natural, que e a que interessa medir. O
 * teste nao afirma qual e mais rapida - isso depende da maquina - mas afirma as
 * propriedades que nao dependem dela, e imprime os tempos para a comparacao entrar no ADR
 * com numero, e nao com opiniao.
 */
class ComparacaoSobCargaTest extends BancoDeTestes {

    private static final int ESTOQUE_INICIAL = 50;
    private static final int TENTATIVAS = 200;
    private static final int THREADS = 24;

    @Autowired
    private EstoqueComLockOtimista otimista;

    @Autowired
    private EstoqueComLockPessimista pessimista;

    @Test
    @DisplayName("nenhuma das duas vende a mais, e a fila do pessimista nao perde venda")
    void comparaAsDuasEstrategias() throws Exception {
        Medicao comOtimista = medir(otimista);
        Medicao comPessimista = medir(pessimista);

        System.out.printf("%n%-12s %10s %10s %12s %14s %10s%n",
                "estrategia", "reservadas", "sem estoque", "concorrencia", "retentativas", "tempo");
        System.out.println(comOtimista);
        System.out.println(comPessimista);

        // A invariante que nenhuma estrategia correta pode violar.
        assertThat(comOtimista.reservado + comOtimista.disponivel).isEqualTo(ESTOQUE_INICIAL);
        assertThat(comPessimista.reservado + comPessimista.disponivel).isEqualTo(ESTOQUE_INICIAL);
        assertThat(comOtimista.reservado).isLessThanOrEqualTo(ESTOQUE_INICIAL);
        assertThat(comPessimista.reservado).isLessThanOrEqualTo(ESTOQUE_INICIAL);

        // A fila atende todo mundo: o pessimista nunca desiste, entao vende o estoque
        // inteiro. E a propriedade que o justifica quando cada venda perdida custa caro.
        assertThat(comPessimista.reservado)
                .as("o lock pessimista nao perde venda por disputa")
                .isEqualTo(ESTOQUE_INICIAL);

        // O otimista pode perder venda quando o retry esgota - e nesse caso o cliente
        // recebe "tente de novo", e nao "acabou". Ver ADR 0002.
        assertThat(comOtimista.porConcorrencia)
                .as("vendas perdidas por disputa, e nao por falta de estoque")
                .isGreaterThanOrEqualTo(0);
    }

    private Medicao medir(Estoque estoque) throws Exception {
        String sku = "CARGA-" + estoque.nome();
        cadastrarProduto(sku, ESTOQUE_INICIAL);
        otimista.zerarContador();

        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < TENTATIVAS; i++) {
            String cliente = "CLI-" + i;
            tarefas.add(() -> {
                estoque.reservar(sku, cliente, 1);
                return null;
            });
        }

        long inicio = System.nanoTime();
        List<Resultado> resultados = emParalelo(THREADS, tarefas);
        Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);

        return new Medicao(
                estoque.nome(),
                reservas.totalReservadoDe(sku),
                quantidadeEmEstoque(sku),
                resultados.stream().filter(r -> r.falhouCom(SemEstoque.class)).count(),
                resultados.stream().filter(r -> r.falhouCom(ConcorrenciaExcessiva.class)).count(),
                otimista.retentativas(),
                duracao);
    }

    private record Medicao(String nome, long reservado, long disponivel, long porFaltaDeEstoque,
                           long porConcorrencia, long retentativas, Duration duracao) {

        @Override
        public String toString() {
            return "%-12s %10d %10d %12d %14d %8d ms".formatted(
                    nome, reservado, porFaltaDeEstoque, porConcorrencia, retentativas, duracao.toMillis());
        }
    }
}
