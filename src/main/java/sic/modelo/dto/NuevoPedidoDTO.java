package sic.modelo.dto;

import lombok.Builder;
import lombok.Data;
import sic.modelo.TipoDeEnvio;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
public class NuevoPedidoDTO {

  private Date fechaVencimiento;
  private String observaciones;
  private Long idEmpresa;
  private Long idUsuario;
  private Long idCliente;
  private Long idSucursal;
  private TipoDeEnvio tipoDeEnvio;
  private List<RenglonPedidoDTO> renglones;
  private BigDecimal subTotal;
  private BigDecimal recargoPorcentaje;
  private BigDecimal recargoNeto;
  private BigDecimal descuentoPorcentaje;
  private BigDecimal descuentoNeto;
  private BigDecimal total;

}
