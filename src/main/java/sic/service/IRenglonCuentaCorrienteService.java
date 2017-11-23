package sic.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import sic.modelo.CuentaCorriente;
import sic.modelo.FacturaVenta;
import sic.modelo.Nota;
import sic.modelo.Pago;
import sic.modelo.RenglonCuentaCorriente;

public interface IRenglonCuentaCorrienteService {
    
    RenglonCuentaCorriente guardar(RenglonCuentaCorriente renglonCuentaCorriente);
    
    RenglonCuentaCorriente getRenglonCuentaCorrienteDeFactura(FacturaVenta fv, boolean eliminado);
    
    RenglonCuentaCorriente getRenglonCuentaCorrienteDeNota(Nota n, boolean eliminado);
    
    RenglonCuentaCorriente getRenglonCuentaCorrienteDePago(Pago p, boolean eliminado);
    
    Page<RenglonCuentaCorriente> getRenglonesCuentaCorriente(CuentaCorriente cuentaCorriente, boolean eliminado, Pageable page);
    
    Double getSaldoCuentaCorriente(long idCuentaCorriente);
    
}
