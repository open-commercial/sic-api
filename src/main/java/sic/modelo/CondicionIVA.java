package sic.modelo;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "condicioniva")
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = {"nombre"})
@ToString
public class CondicionIVA implements Serializable {

    @Id
    @GeneratedValue
    private long id_CondicionIVA;

    @Column(nullable = false)
    private String nombre;

    private boolean discriminaIVA;

    private boolean eliminada;

}