package sic.service;

import java.util.List;
import sic.modelo.BusquedaTransportistaCriteria;
import sic.modelo.Empresa;
import sic.modelo.Transportista;

import javax.validation.Valid;

public interface ITransportistaService {

    Transportista getTransportistaPorId(long id_Transportista);
            
    void actualizar(@Valid Transportista transportista);

    List<Transportista> buscarTransportistas(BusquedaTransportistaCriteria criteria);

    void eliminar(long idTransportista);

    Transportista getTransportistaPorNombre(String nombre, Empresa empresa);

    List<Transportista> getTransportistas(Empresa empresa);

    Transportista guardar(@Valid Transportista transportista);
    
}
