package sic.modelo.dto;

import lombok.*;

@Data
@EqualsAndHashCode(exclude = {"id_Medida", "idEmpresa", "nombreEmpresa"})
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MedidaDTO {

  private long id_Medida;
  private String nombre;
  private Long idEmpresa;
  private String nombreEmpresa;
  private boolean eliminada;
}
