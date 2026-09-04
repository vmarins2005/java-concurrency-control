package io.github.vmarins2005.estoque;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Aplicacao {

    public static void main(String[] argumentos) {
        SpringApplication.run(Aplicacao.class, argumentos);
    }

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
