package io.github.vmarins2005.estoque;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String sku;

    @Column(name = "cliente_id", nullable = false)
    private String clienteId;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected Reserva() {
        // exigido pelo JPA
    }

    public Reserva(String sku, String clienteId, int quantidade, Instant criadaEm) {
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.clienteId = clienteId;
        this.quantidade = quantidade;
        this.criadaEm = criadaEm;
    }

    public UUID id() {
        return id;
    }

    public String sku() {
        return sku;
    }

    public int quantidade() {
        return quantidade;
    }
}
