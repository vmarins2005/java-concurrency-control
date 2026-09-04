package io.github.vmarins2005.estoque;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Ponto de encontro que segura todas as threads entre a leitura e a escrita, e as libera
 * juntas.
 *
 * <p>Sem isso, provocar a corrida dependeria de sorte de agendamento: o teste passaria numa
 * maquina e falharia em outra, ou pior, passaria por acidente exatamente quando o codigo
 * esta errado. Com o ponto de encontro, a janela e garantida e o resultado e deterministico.
 *
 * <p>Cada thread so espera <b>uma vez</b>. Isso importa por causa do retry do lock otimista:
 * na segunda tentativa a thread precisa passar direto, senao ficaria esperando por um grupo
 * que ja se dispersou.
 *
 * <p><b>Nao serve para o lock pessimista</b>, e a razao e instrutiva: la o lock e adquirido
 * <i>antes</i> da janela, entao so a primeira thread chega aqui - as outras estao bloqueadas
 * no {@code SELECT ... FOR UPDATE}. O ponto de encontro nunca completaria. Essa
 * impossibilidade e exatamente a propriedade que o lock pessimista oferece.
 */
final class JanelaDeCorrida implements Runnable {

    private final CountDownLatch chegaram;
    private final ThreadLocal<Boolean> jaPassou = ThreadLocal.withInitial(() -> false);

    JanelaDeCorrida(int quantasThreads) {
        this.chegaram = new CountDownLatch(quantasThreads);
    }

    @Override
    public void run() {
        if (jaPassou.get()) {
            return;
        }
        jaPassou.set(true);
        chegaram.countDown();
        try {
            if (!chegaram.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("as threads nao se encontraram na janela");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido na janela", e);
        }
    }
}
