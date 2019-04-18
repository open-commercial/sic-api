package sic.modelo;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.querydsl.core.annotations.QueryInit;
import java.io.Serializable;
import javax.persistence.*;
import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;

@Entity
@Table(name = "transportista")
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"nombre", "empresa"})
@ToString
@JsonIgnoreProperties({"localidad", "empresa", "ubicacion", "eliminado"})
public class Transportista implements Serializable {

  @Id @GeneratedValue private long id_Transportista;

  @Column(nullable = false)
  @NotNull(message = "{mensaje_transportista_nombre_vacio}")
  @NotEmpty(message = "{mensaje_transportista_nombre_vacio}")
  private String nombre;

  @OneToOne
  @JoinColumn(name = "idUbicacion", referencedColumnName = "idUbicacion")
  @QueryInit("localidad.provincia")
  private Ubicacion ubicacion;

  @Column(nullable = false)
  private String web;

  @Column(nullable = false)
  private String telefono;

  @ManyToOne
  @JoinColumn(name = "id_Empresa", referencedColumnName = "id_Empresa")
  @NotNull(message = "{mensaje_transportista_empresa_vacia}")
  private Empresa empresa;

  private boolean eliminado;


  @JsonGetter("idEmpresa")
  public Long getIdEmpresa() {
    return empresa.getId_Empresa();
  }

  @JsonGetter("nombreEmpresa")
  public String getNombreEmpresa() {
    return empresa.getNombre();
  }

  @JsonGetter("idUbicacion")
  public Long getidUbicacion() {
    if (ubicacion != null) {
      return ubicacion.getIdUbicacion();
    } else {
      return null;
    }
  }

  @JsonGetter("detalleUbicacion")
  public String getDetalleUbicacion() {
    if (ubicacion != null) {
      return ubicacion.toString();
    } else {
      return null;
    }
  }
}
