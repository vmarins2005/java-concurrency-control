package io.github.vmarins2005.estoque;

public class SemEstoque extends RuntimeException {

    public SemEstoque(String sku, int disponivel, int pedido) {
        super("estoque insuficiente para %s: disponivel %d, pedido %d".formatted(sku, disponivel, pedido));
    }
}
