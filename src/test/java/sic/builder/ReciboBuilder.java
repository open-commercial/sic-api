package sic.builder;

import java.util.Date;
import sic.modelo.Cliente;
import sic.modelo.Empresa;
import sic.modelo.FormaDePago;
import sic.modelo.Recibo;
import sic.modelo.Usuario;


public class ReciboBuilder {
    
    private Long idRecibo = 0L;
    private long nroRecibo = 1L;
    private boolean eliminado = false;
    private String observacion= "Recibo por contratar al GOLDEN ARMY";
    private FormaDePago formaDePago = new FormaDePagoBuilder().build();
    private Empresa empresa = new EmpresaBuilder().build();
    private Cliente cliente = new ClienteBuilder().build();
    private Usuario usuario = new UsuarioBuilder().build();
    private Date fecha = new Date();    
    private double monto = 1000;  
    private double saldoSobrante = 200;
    
    public Recibo build() {
      return new Recibo(idRecibo, nroRecibo, eliminado, observacion, formaDePago, empresa, cliente, usuario, fecha, monto, saldoSobrante);
    }

    public ReciboBuilder withidRecibo(Long idRecibo) {
        this.idRecibo = idRecibo;
        return this;
    }
    
    public ReciboBuilder withNroRecibo(Long nroRecibo) {
        this.nroRecibo = nroRecibo;
        return this;
    }
    
    public ReciboBuilder withEliminado(boolean eliminado) {
        this.eliminado = eliminado;
        return this;
    }
    
    public ReciboBuilder withObservacion(String observacion) {
        this.observacion = observacion;
        return this;
    }
    
    public ReciboBuilder withFormaDePago(FormaDePago formaDePago) {
        this.formaDePago = formaDePago;
        return this;
    }
    
    public ReciboBuilder withEmpresa(Empresa empresa) {
        this.empresa = empresa;
        return this;
    }
    
    public ReciboBuilder withClientes(Cliente cliente) {
        this.cliente = cliente;
        return this;
    }
    
    public ReciboBuilder withUsuario(Usuario usuario) {
        this.usuario = usuario;
        return this;
    }
    
    public ReciboBuilder withFecha(Date fecha) {
        this.fecha = fecha;
        return this;
    }
    
    public ReciboBuilder withMonto(double monto) {
        this.monto = monto;
        return this;
    }
    
    public ReciboBuilder withSaldoSobrante(double saldoSobrante) {
        this.saldoSobrante = saldoSobrante;
        return this;
    }
    
}
