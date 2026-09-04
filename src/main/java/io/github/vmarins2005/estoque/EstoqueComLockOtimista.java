package io.github.vmarins2005.estoque;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Lock otimista com retry.
 *
 * <p>Ninguem espera por ninguem: quem perde a corrida recebe excecao no commit e tenta de
 * novo, relendo o estado atual. Na segunda leitura o estoque ja mudou - e a tentativa
 * termina, corretamente, em {@link SemEstoque} se acabou.
 *
 * <p>O retry acontece <b>fora</b> da transacao. Cada tentativa e uma transacao nova: repetir
 * dentro da transacao ja invalidada nao adianta, porque o contexto de persistencia continua
 * sujo.
 *
 * <p>Backoff exponencial com jitter. Sem jitter, as tentativas que colidiram voltam todas
 * juntas no mesmo instante e colidem de novo - o proprio retry vira a causa da contencao.
 */
@Service
public class EstoqueComLockOtimista implements Estoque {

    private final TransacaoDeReserva transacao;
    private final int maximoDeTentativas;
    private final AtomicLong retentativas = new AtomicLong();

    EstoqueComLockOtimista(TransacaoDeReserva transacao,
                           @Value("${estoque.maximo-de-tentativas:5}") int maximoDeTentativas) {
        this.transacao = transacao;
        this.maximoDeTentativas = maximoDeTentativas;
    }

    @Override
    public String nome() {
        return "otimista";
    }

    @Override
    public void reservar(String sku, String clienteId, int quantidade, Runnable janelaDeCorrida) {
        for (int tentativa = 1; tentativa <= maximoDeTentativas; tentativa++) {
            try {
                transacao.comLockOtimista(sku, clienteId, quantidade, janelaDeCorrida);
                return;
            } catch (ObjectOptimisticLockingFailureException conflito) {
                retentativas.incrementAndGet();
                if (tentativa == maximoDeTentativas) {
                    throw new ConcorrenciaExcessiva(sku, maximoDeTentativas);
                }
                esperarAntesDeTentarDeNovo(tentativa);
            }
        }
    }

    /** Quantas vezes o conflito aconteceu - o numero que decide se esta estrategia serve. */
    public long retentativas() {
        return retentativas.get();
    }

    public void zerarContador() {
        retentativas.set(0);
    }

    private void esperarAntesDeTentarDeNovo(int tentativa) {
        long tetoEmMilissegundos = Math.min(50L, 2L << tentativa);
        long espera = ThreadLocalRandom.current().nextLong(1, tetoEmMilissegundos + 1);
        try {
            Thread.sleep(espera);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("retry interrompido", e);
        }
    }
}
