package sic.modelo.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import sic.modelo.Cliente;
import sic.modelo.Empresa;
import sic.modelo.EstadoPedido;
import sic.modelo.Factura;
import sic.modelo.Usuario;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"facturas", "renglones"})
@EqualsAndHashCode(of = {"nroPedido", "empresa"})
public class PedidoDTO implements Serializable {
    
    private long id_Pedido;
    private long nroPedido;    
    private Date fecha;    
    private Date fechaVencimiento;    
    private String observaciones;    
    private Empresa empresa;
    private boolean eliminado;    
    private Cliente cliente;    
    private Usuario usuario;        
    private List<Factura> facturas;        
    private List<RenglonPedidoDTO> renglones;
    private double totalEstimado;
    private double totalActual;
    private EstadoPedido estado;
}
