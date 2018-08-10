package sic.modelo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocalidadDTO {

  private long id_Localidad;
  private String nombre;
  private String codigoPostal;
  private ProvinciaDTO provincia;
  private boolean eliminada;

}