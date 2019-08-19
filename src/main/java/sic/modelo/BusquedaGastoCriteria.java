package sic.modelo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BusquedaGastoCriteria {

  private boolean buscaPorFecha;
  private Date fechaDesde;
  private Date fechaHasta;
  private Long idSucursal;
  private boolean buscaPorUsuario;
  private Long idUsuario;
  private boolean buscarPorFormaDePago;
  private Long idFormaDePago;
  private boolean buscaPorNro;
  private Long nroGasto;
  private boolean buscaPorConcepto;
  private String concepto;
  private Pageable pageable;
}
