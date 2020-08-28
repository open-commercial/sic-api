package sic.modelo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Entity
@Table(name = "favorito")
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"cliente"})
public class ProductoFavorito implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idFavorito")
    private long idFavorito;

    @OneToOne
    @JoinColumn(name = "id_Cliente", referencedColumnName = "id_Cliente")
    @NotNull(message = "{mensaje_cliente_vacio}")
    private Cliente cliente;

    @OneToOne
    @JoinColumn(name = "idProducto", referencedColumnName = "idProducto")
    @NotNull(message = "{mensaje_producto_vacio}")
    private Producto producto;

}
