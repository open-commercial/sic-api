package sic.modelo;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import javax.persistence.*;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import javax.validation.constraints.NotEmpty;
import sic.controller.Views;

@Entity
@Table(name = "producto")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "descripcion")
@ToString
@JsonIgnoreProperties({"medida", "rubro", "proveedor", "sucursal"})
public class Producto implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @JsonView(Views.Public.class)
  private Long idProducto;

  @JsonView(Views.Public.class)
  private String codigo;

  @NotNull(message = "{mensaje_producto_vacio_descripcion}")
  @NotEmpty(message = "{mensaje_producto_vacio_descripcion}")
  @JsonView(Views.Public.class)
  private String descripcion;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "idProducto")
  @JsonProperty(access = JsonProperty.Access.READ_WRITE)
  @NotEmpty
  private List<CantidadEnSucursal> cantidadEnSucursales;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_cantidad_negativa}")
  private BigDecimal cantidad;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_cantidadMinima_negativa}")
  private BigDecimal cantMinima;

  @Transient
  @JsonView(Views.Public.class)
  private boolean hayStock;

  @Transient
  @JsonView(Views.Public.class)
  private BigDecimal precioBonificado;

  @Column(precision = 25, scale = 15)
  @DecimalMin(
      value = "0",
      inclusive = false,
      message = "{mensaje_producto_cantidad_bulto_invalida}")
  @JsonView(Views.Public.class)
  private BigDecimal bulto;

  @ManyToOne
  @JoinColumn(name = "id_Medida", referencedColumnName = "id_Medida")
  @NotNull(message = "{mensaje_producto_vacio_medida}")
  private Medida medida;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_precioCosto_negativo}")
  private BigDecimal precioCosto;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_gananciaPorcentaje_negativo}")
  private BigDecimal gananciaPorcentaje;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_gananciaNeto_negativo}")
  private BigDecimal gananciaNeto;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_venta_publico_negativo}")
  private BigDecimal precioVentaPublico;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_IVAPorcentaje_negativo}")
  private BigDecimal ivaPorcentaje;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_IVANeto_negativo}")
  private BigDecimal ivaNeto;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_producto_precioLista_negativo}")
  @JsonView(Views.Public.class)
  private BigDecimal precioLista;

  @ManyToOne
  @JoinColumn(name = "id_Rubro", referencedColumnName = "id_Rubro")
  @NotNull(message = "{mensaje_producto_vacio_rubro}")
  private Rubro rubro;

  private boolean ilimitado;

  private boolean publico;

  private boolean destacado;

  @Temporal(TemporalType.TIMESTAMP)
  private Date fechaUltimaModificacion;

//  private String estanteria;
//
//  private String estante;

  @ManyToOne
  @JoinColumn(name = "id_Proveedor", referencedColumnName = "id_Proveedor")
  @NotNull(message = "{mensaje_producto_vacio_proveedor}")
  private Proveedor proveedor;

  @NotNull(message = "{mensaje_producto_vacio_nota}")
  private String nota;

  @Column(nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date fechaAlta;

  @Temporal(TemporalType.TIMESTAMP)
  private Date fechaVencimiento;

  private boolean eliminado;

  @JsonView(Views.Public.class)
  private String urlImagen;

  @JsonGetter("nombreMedida")
  @JsonView(Views.Public.class)
  public String getNombreMedida() {
    return medida.getNombre();
  }

  @JsonGetter("nombreRubro")
  public String getNombreRubro() {
    return rubro.getNombre();
  }

  @JsonGetter("razonSocialProveedor")
  public String getRazonSocialProveedor() {
    return proveedor.getRazonSocial();
  }
}
