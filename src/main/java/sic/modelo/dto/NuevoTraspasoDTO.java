package sic.modelo.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NuevoTraspasoDTO {

  private Map<Long, BigDecimal> productosAndCantidades;
  private Long idSucursalOrigen;
  private Long idSucursalDestino;
  private Long idUsuario;
}