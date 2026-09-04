package io.github.vmarins2005.estoque;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres de verdade, e nao H2: semantica de lock e exatamente o tipo de coisa que um
 * banco em memoria simula mal. {@code SELECT ... FOR UPDATE} e o comportamento do
 * {@code @Version} sob concorrencia so valem alguma coisa medidos contra o banco real.
 *
 * <p>Isolamento por truncate, pelo mesmo motivo do projeto de Testcontainers desta serie:
 * os testes precisam de commits de verdade, entao rollback no fim nao serve.
 */
@SpringBootTest
abstract class BancoDeTestes {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("estoque")
                    .withUsername("estoque")
                    .withPassword("estoque");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void apontarParaOContainer(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RepositorioDeProdutos produtos;

    @Autowired
    protected RepositorioDeReservas reservas;

    @BeforeEach
    void limparTabelas() {
        jdbc.execute("TRUNCATE TABLE reserva");
        jdbc.execute("TRUNCATE TABLE produto");
    }

    protected void cadastrarProduto(String sku, int quantidade) {
        jdbc.update("INSERT INTO produto (sku, nome, quantidade_disponivel, versao) VALUES (?, ?, ?, 0)",
                sku, "Produto " + sku, quantidade);
    }

    protected int quantidadeEmEstoque(String sku) {
        Integer quantidade = jdbc.queryForObject(
                "SELECT quantidade_disponivel FROM produto WHERE sku = ?", Integer.class, sku);
        return quantidade == null ? 0 : quantidade;
    }

    /**
     * Roda as tarefas em paralelo e devolve o resultado de cada uma - inclusive as que
     * falharam, porque neste projeto <b>as falhas sao o dado</b>.
     */
    protected List<Resultado> emParalelo(int threads, List<Callable<Void>> tarefas) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Void>> futuros = new ArrayList<>();
            for (Callable<Void> tarefa : tarefas) {
                futuros.add(executor.submit(tarefa));
            }

            List<Resultado> resultados = new ArrayList<>();
            for (Future<Void> futuro : futuros) {
                try {
                    futuro.get(60, TimeUnit.SECONDS);
                    resultados.add(new Resultado(true, null));
                } catch (Exception e) {
                    Throwable causa = e.getCause() == null ? e : e.getCause();
                    resultados.add(new Resultado(false, causa));
                }
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }

    protected record Resultado(boolean sucesso, Throwable falha) {

        boolean falhouCom(Class<? extends Throwable> tipo) {
            return !sucesso && tipo.isInstance(falha);
        }
    }
}
