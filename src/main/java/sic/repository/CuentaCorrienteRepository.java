package sic.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import sic.modelo.Cliente;
import sic.modelo.CuentaCorriente;

public interface CuentaCorrienteRepository extends PagingAndSortingRepository<CuentaCorriente, Long> {
    
      @Query("SELECT c FROM CuentaCorriente c WHERE c.idCuentaCorriente = :idCuentaCorriente AND c.eliminada = false")
      CuentaCorriente findById(@Param("idCuentaCorriente") long idCuentaCorriente);
      
      CuentaCorriente findByClienteAndEliminada(Cliente cliente, boolean eliminada);

      @Query("SELECT SUM(r.monto) FROM CuentaCorriente cc INNER JOIN cc.renglones r WHERE cc.cliente.id_Cliente = :idCliente AND cc.eliminada = false AND r.eliminado = false")
      Double getSaldoCuentaCorriente(@Param("idCliente") long idCliente);
      
}
