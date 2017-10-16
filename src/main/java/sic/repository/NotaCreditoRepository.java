package sic.repository;

import java.util.Date;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sic.modelo.Cliente;
import sic.modelo.Empresa;
import sic.modelo.NotaCredito;

public interface NotaCreditoRepository extends NotaRepository<NotaCredito> { 
    
    @Query("SELECT nc FROM NotaCredito nc WHERE nc.idNota = :idNotaCredito AND nc.eliminada = false")
    NotaCredito getById(@Param("idNotaCredito") long idNotaCredito);
    
    @Query("SELECT SUM(nc.total) FROM NotaCredito nc WHERE nc.empresa = :empresa AND nc.cliente = :cliente AND nc.eliminada = false AND nc.fecha <= :hasta")
    Double totalNotasCredito(@Param("hasta") Date hasta, @Param("cliente") Cliente cliente, @Param("empresa") Empresa empresa);
    
    NotaCredito findTopByClienteAndEmpresaAndEliminadaOrderByNroNotaDesc(Cliente cliente, Empresa empresa, boolean eliminada);
    
}
