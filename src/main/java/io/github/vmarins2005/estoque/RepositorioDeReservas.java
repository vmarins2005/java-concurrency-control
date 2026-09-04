package io.github.vmarins2005.estoque;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositorioDeReservas extends JpaRepository<Reserva, UUID> {

    @Query("SELECT COALESCE(SUM(r.quantidade), 0) FROM Reserva r WHERE r.sku = :sku")
    long totalReservadoDe(@Param("sku") String sku);

    long countBySku(String sku);
}
