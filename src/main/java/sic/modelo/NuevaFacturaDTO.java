package sic.modelo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sic.modelo.dto.NuevoRenglonFacturaDTO;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NuevaFacturaDTO {

  private Long idSucursal;
  private Long idPedido;
  private Long idCliente;
  private Long idTransportista;
  private TipoDeComprobante tipoDeComprobante;
  private String observaciones;
  private List<NuevoRenglonFacturaDTO> renglones;
  private Long[] idsFormaDePago;
  private BigDecimal[] montos;
  private int[] indices;
  private BigDecimal recargoPorcentaje;
  private BigDecimal descuentoPorcentaje;
}
