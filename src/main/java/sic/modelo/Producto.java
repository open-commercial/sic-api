package sic.modelo;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.Email;

@Entity
@Table(name = "producto")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"descripcion", "empresa"})
@ToString
@JsonIgnoreProperties({"medida", "rubro", "proveedor", "empresa"})
public class Producto implements Serializable {

    @Id
    @GeneratedValue
    private long id_Producto;
    
    private String codigo;

    @NotNull
    private String descripcion;

    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0")
    private BigDecimal cantidad;

    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0")
    private BigDecimal cantMinima;
    
    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0", inclusive= false)
    private BigDecimal ventaMinima;

    @ManyToOne
    @JoinColumn(name = "id_Medida", referencedColumnName = "id_Medida")
    private Medida medida;
        
    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0", inclusive= false)
    private BigDecimal precioCosto;
    
    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0", inclusive= false)
    private BigDecimal ganancia_porcentaje;
    
    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0", inclusive = false)
    private BigDecimal ganancia_neto;
    
    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0", inclusive = false)
    private BigDecimal precioVentaPublico;
    
    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0", inclusive= false)
    private BigDecimal iva_porcentaje;
    
    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0", inclusive= false)
    private BigDecimal iva_neto;
    
    @Column(precision = 25, scale = 15)
    //@DecimalMin(value= "0", inclusive = false)
    private BigDecimal impuestoInterno_porcentaje;
    
    @Column(precision = 25, scale = 15)
    //@DecimalMin(value= "0", inclusive = false)
    private BigDecimal impuestoInterno_neto;
    
    @Column(precision = 25, scale = 15)
    @DecimalMin(value= "0", inclusive = false)
    private BigDecimal precioLista;

    @ManyToOne
    @JoinColumn(name = "id_Rubro", referencedColumnName = "id_Rubro")
    private Rubro rubro;
        
    private boolean ilimitado;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    @Past
    private Date fechaUltimaModificacion;

    @NotNull
    private String estanteria;

    @NotNull
    private String estante;
    
    @ManyToOne
    @JoinColumn(name = "id_Proveedor", referencedColumnName = "id_Proveedor")
    private Proveedor proveedor;
        
    @NotNull
    private String nota;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaAlta;

    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaVencimiento;

    @ManyToOne
    @JoinColumn(name = "id_Empresa", referencedColumnName = "id_Empresa")
    private Empresa empresa;
    
    private boolean eliminado;

    @JsonGetter("nombreMedida")
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
    
    @JsonGetter("nombreEmpresa")
    public String getNombreEmpresa() {
        return empresa.getNombre();
    }

}