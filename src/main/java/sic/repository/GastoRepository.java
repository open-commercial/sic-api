package sic.repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import sic.modelo.Sucursal;
import sic.modelo.Gasto;

public interface GastoRepository extends PagingAndSortingRepository<Gasto, Long>, QuerydslPredicateExecutor<Gasto>, GastoRepositoryCustom {

    @Query("SELECT g FROM Gasto g " +
            "WHERE g.sucursal.idSucursal = :idSucursal " +
            "AND g.formaDePago.id_FormaDePago = :idFormaDePago " +
            "AND g.fecha BETWEEN :desde AND :hasta AND g.eliminado = false")
    List<Gasto> getGastosEntreFechasPorFormaDePago(@Param("idSucursal") long idSucursal,
                                                   @Param("idFormaDePago") long idFormaDePago,
                                                   @Param("desde") Date desde, @Param("hasta") Date hasta);

    Gasto findTopBySucursalAndEliminadoOrderByNroGastoDesc(Sucursal sucursal, boolean eliminado);

    @Query("SELECT SUM(g.monto) FROM Gasto g " +
            "WHERE g.sucursal.idSucursal = :idSucursal " +
            "AND g.formaDePago.id_FormaDePago = :idFormaDePago " +
            "AND g.fecha BETWEEN :desde AND :hasta AND g.eliminado = false")
    BigDecimal getTotalGastosEntreFechasPorFormaDePago(@Param("idSucursal") long idSucursal, @Param("idFormaDePago") long idFormaDePago,
                                                       @Param("desde") Date desde, @Param("hasta") Date hasta);

    @Query("SELECT SUM(g.monto) FROM Gasto g " +
            "WHERE g.sucursal.idSucursal = :idSucursal " +
            "AND g.formaDePago.afectaCaja = true " +
            "AND g.fecha BETWEEN :desde AND :hasta AND g.eliminado = false")
    BigDecimal getTotalGastosQueAfectanCajaEntreFechas(@Param("idSucursal") long idSucursal,
                                                       @Param("desde") Date desde, @Param("hasta") Date hasta);

    @Query("SELECT SUM(g.monto) FROM Gasto g " +
            "WHERE g.sucursal.idSucursal = :idSucursal " +
            "AND g.fecha BETWEEN :desde AND :hasta AND g.eliminado = false")
    BigDecimal getTotalGastosEntreFechas(@Param("idSucursal") long idSucursal,
                                         @Param("desde") Date desde, @Param("hasta") Date hasta);

}
