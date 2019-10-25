package sic.modelo;

import com.fasterxml.jackson.annotation.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.*;
import javax.validation.constraints.DecimalMin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "nota")
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"fecha", "tipoComprobante", "serie", "nroNota", "empresa"})
@JsonIdentityInfo(
    generator = ObjectIdGenerators.PropertyGenerator.class,
    property = "idNota",
    scope = Nota.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = NotaCredito.class),
  @JsonSubTypes.Type(value = NotaDebito.class)
})
@JsonIgnoreProperties({"cliente", "empresa", "usuario","facturaVenta", "proveedor", "facturaCompra", "recibo"})
public abstract class Nota implements Serializable {

  @JsonGetter(value = "type")
  public String getType() {
    return this.getClass().getSimpleName();
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long idNota;

  @Column(nullable = false)
  private long serie;

  @Column(nullable = false)
  private long nroNota;

  private boolean eliminada;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private TipoDeComprobante tipoComprobante;

  @NotNull(message = "{mensaje_nota_fecha_vacia}")
  private LocalDateTime fecha;

  @ManyToOne
  @JoinColumn(name = "id_Empresa", referencedColumnName = "id_Empresa")
  private Empresa empresa;

  @ManyToOne
  @JoinColumn(name = "id_Usuario", referencedColumnName = "id_Usuario")
  private Usuario usuario;

  @ManyToOne
  @JoinColumn(name = "id_Cliente", referencedColumnName = "id_Cliente")
  private Cliente cliente;

  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "id_FacturaVenta")
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private FacturaVenta facturaVenta;

  @ManyToOne
  @JoinColumn(name = "id_Proveedor", referencedColumnName = "id_Proveedor")
  private Proveedor proveedor;

  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "id_FacturaCompra")
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private FacturaCompra facturaCompra;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private Movimiento movimiento;

  @Column(nullable = false)
  @NotBlank(message = "{mensaje_nota_de_motivo_vacio}")
  private String motivo;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_sub_total_bruto}")
  private BigDecimal subTotalBruto;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_iva_21_neto_negativo}")
  private BigDecimal iva21Neto;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_iva_105_neto_negativo}")
  private BigDecimal iva105Neto;

  @Column(precision = 25, scale = 15)
  @DecimalMin(value = "0", message = "{mensaje_total_negativo}")
  private BigDecimal total;

  private long cae;

  private LocalDateTime vencimientoCae;

  private long numSerieAfip;

  private long numNotaAfip;

  @JsonGetter("idEmpresa")
  public Long getIdEmpresa() {
    return empresa.getId_Empresa();
  }

  @JsonGetter("nombreEmpresa")
  public String getNombreEmpresa() {
    return empresa.getNombre();
  }

  @JsonGetter("idCliente")
  public Long getIdCliente() {
    if (cliente != null) {
      return cliente.getId_Cliente();
    } else {
      return null;
    }
  }

  @JsonGetter("nombreFiscalCliente")
  public String getNombreFiscalCliente() {
    if (cliente != null) {
      return cliente.getNombreFiscal();
    } else {
      return null;
    }
  }

  @JsonGetter("idViajante")
  public Long getIdViajante() {
    if (cliente != null && cliente.getViajante() != null) {
      return cliente.getViajante().getId_Usuario();
    } else {
      return null;
    }
  }

  @JsonGetter("nombreViajante")
  public String getNombreViajante() {
    if (cliente != null && cliente.getViajante() != null) {
      return cliente.getViajante().getNombre() + " " + cliente.getViajante().getApellido() + " (" + cliente.getViajante().getUsername() + ")";
    } else {
      return null;
    }
  }


  @JsonGetter("idProveedor")
  public Long getIdProveedor() {
    if (proveedor != null) {
      return proveedor.getId_Proveedor();
    } else {
      return null;
    }
  }

  @JsonGetter("razonSocialProveedor")
  public String getRazonSocialProveedor() {
    if (proveedor != null) {
      return proveedor.getRazonSocial();
    } else {
      return null;
    }
  }

  @JsonGetter("idFacturaVenta")
  public Long getIdFacturaVenta() {
    if (facturaVenta != null) {
      return facturaVenta.getId_Factura();
    } else {
      return null;
    }
  }

  @JsonGetter("idFacturaCompra")
  public Long getIdFacturaCompra() {
    if (facturaCompra != null) {
      return facturaCompra.getId_Factura();
    } else {
      return null;
    }
  }

  @JsonGetter("idUsuario")
  public Long getIdUsuario() {
    return usuario.getId_Usuario();
  }

  @JsonGetter("nombreUsuario")
  public String getNombreUsuario() {
    return usuario.getNombre() + " " + usuario.getApellido() + " (" + usuario.getUsername() + ")";
  }
}
