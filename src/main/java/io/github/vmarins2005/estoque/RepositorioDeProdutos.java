package io.github.vmarins2005.estoque;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeProdutos extends JpaRepository<Produto, String> {

    /**
     * {@code SELECT ... FOR UPDATE}. A transacao segura a linha ate comitar; quem chegar
     * depois espera.
     *
     * <p>Nada de {@code SKIP LOCKED} aqui: numa fila, pular a linha travada e o certo; numa
     * reserva de estoque, pular significaria dizer ao cliente que nao ha estoque quando ha.
     * Ele precisa esperar a vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Produto p WHERE p.sku = :sku")
    Optional<Produto> buscarParaAtualizar(@Param("sku") String sku);

    /**
     * Leitura crua, sem trazer a entidade para o contexto de persistencia.
     *
     * <p>Existe para a versao ingenua conseguir ser ingenua: se ela carregasse a entidade
     * gerenciada, o {@code @Version} entraria em acao no flush e o defeito nao apareceria.
     * O que o teste precisa demonstrar e o codigo que ninguem pensou em proteger.
     */
    @Query(value = "SELECT quantidade_disponivel FROM produto WHERE sku = :sku", nativeQuery = true)
    Integer quantidadeAtual(@Param("sku") String sku);

    /**
     * {@code UPDATE} com valor absoluto e sem verificar versao - exatamente o que se escreve
     * quando nao se pensou em concorrencia.
     */
    @Modifying
    @Query(value = "UPDATE produto SET quantidade_disponivel = :quantidade WHERE sku = :sku",
            nativeQuery = true)
    void gravarQuantidade(@Param("sku") String sku, @Param("quantidade") int quantidade);
}
