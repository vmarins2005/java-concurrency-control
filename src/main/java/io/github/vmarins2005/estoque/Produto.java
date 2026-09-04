package io.github.vmarins2005.estoque;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "produto")
public class Produto {

    @Id
    private String sku;

    @Column(nullable = false)
    private String nome;

    @Column(name = "quantidade_disponivel", nullable = false)
    private int quantidadeDisponivel;

    /**
     * O contador de versao do lock otimista.
     *
     * <p>A cada {@code UPDATE} o Hibernate acrescenta {@code AND versao = :versaoLida} e
     * incrementa o campo. Se outra transacao alterou a linha no meio, zero linhas sao
     * afetadas e o Hibernate lanca - em vez de sobrescrever o trabalho alheio.
     *
     * <p>Repare que quem faz o trabalho e a clausula {@code WHERE}, e nao um lock: o banco
     * nao segura nada, e nenhuma transacao espera por outra. E por isso que o lock otimista
     * escala melhor sob contencao baixa.
     */
    @Version
    private long versao;

    protected Produto() {
        // exigido pelo JPA
    }

    public Produto(String sku, String nome, int quantidadeDisponivel) {
        this.sku = sku;
        this.nome = nome;
        this.quantidadeDisponivel = quantidadeDisponivel;
    }

    public void reservar(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade deve ser positiva: " + quantidade);
        }
        if (quantidadeDisponivel < quantidade) {
            throw new SemEstoque(sku, quantidadeDisponivel, quantidade);
        }
        quantidadeDisponivel -= quantidade;
    }

    public String sku() {
        return sku;
    }

    public String nome() {
        return nome;
    }

    public int quantidadeDisponivel() {
        return quantidadeDisponivel;
    }

    public long versao() {
        return versao;
    }
}
