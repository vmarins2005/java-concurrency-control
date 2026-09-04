package io.github.vmarins2005.estoque;

/**
 * O retry esgotou. Nao e "sem estoque" - pode haver estoque de sobra; e disputa demais pela
 * mesma linha.
 *
 * <p>A distincao importa para o cliente: "acabou" pede outra decisao ("quero avisar quando
 * voltar"); "tente de novo" pede a mesma ação outra vez. Confundir as duas na resposta da
 * API e um erro de produto, e nao de codigo. Ver ADR 0002.
 */
public class ConcorrenciaExcessiva extends RuntimeException {

    public ConcorrenciaExcessiva(String sku, int tentativas) {
        super("disputa excessiva pelo produto %s: %d tentativas sem sucesso".formatted(sku, tentativas));
    }
}
