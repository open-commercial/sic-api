package sic.service.impl;

import com.querydsl.core.BooleanBuilder;
import java.util.ArrayList;

import org.springframework.validation.annotation.Validated;
import sic.modelo.*;

import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import javax.persistence.EntityNotFoundException;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sic.service.ITransportistaService;
import sic.service.BusinessServiceException;
import sic.repository.TransportistaRepository;
import sic.service.IUbicacionService;

@Service
@Validated
public class TransportistaServiceImpl implements ITransportistaService {

  private final TransportistaRepository transportistaRepository;
  private final IUbicacionService ubicacionService;
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("Mensajes");

  @Autowired
  public TransportistaServiceImpl(TransportistaRepository transportistaRepository, IUbicacionService ubicacionService) {
    this.transportistaRepository = transportistaRepository;
    this.ubicacionService = ubicacionService;
  }

  @Override
  public Transportista getTransportistaNoEliminadoPorId(long idTransportista) {
    Optional<Transportista> transportista = transportistaRepository
      .findById(idTransportista);
    if (transportista.isPresent() && !transportista.get().isEliminado()) {
      return transportista.get();
    } else {
      throw new EntityNotFoundException(
        ResourceBundle.getBundle("Mensajes")
          .getString("mensaje_transportista_no_existente"));
    }
  }

  @Override
  public List<Transportista> getTransportistas(Empresa empresa) {
    List<Transportista> transportista =
        transportistaRepository.findAllByAndEmpresaAndEliminadoOrderByNombreAsc(empresa, false);
    if (transportista == null) {
      throw new EntityNotFoundException(
          ResourceBundle.getBundle("Mensajes").getString("mensaje_transportista_ninguno_cargado"));
    }
    return transportista;
  }

  @Override
  public List<Transportista> buscarTransportistas(BusquedaTransportistaCriteria criteria) {
    QTransportista qTransportista = QTransportista.transportista;
    BooleanBuilder builder = new BooleanBuilder();
    builder.and(
        qTransportista
            .empresa
            .id_Empresa
            .eq(criteria.getIdEmpresa())
            .and(qTransportista.eliminado.eq(false)));
    if (criteria.isBuscarPorNombre())
      builder.and(this.buildPredicadoNombre(criteria.getNombre(), qTransportista));
    if (criteria.isBuscarPorLocalidad())
      builder.and(qTransportista.ubicacion.localidad.idLocalidad.eq(criteria.getIdLocalidad()));
    if (criteria.isBuscarPorProvincia())
      builder.and(qTransportista.ubicacion.localidad.provincia.idProvincia.eq(criteria.getIdProvincia()));
    List<Transportista> list = new ArrayList<>();
    transportistaRepository
        .findAll(builder, new Sort(Sort.Direction.ASC, "nombre"))
        .iterator()
        .forEachRemaining(list::add);
    return list;
  }

  private BooleanBuilder buildPredicadoNombre(String nombre, QTransportista qtransportista) {
    String[] terminos = nombre.split(" ");
    BooleanBuilder descripcionProducto = new BooleanBuilder();
    for (String termino : terminos) {
      descripcionProducto.and(qtransportista.nombre.containsIgnoreCase(termino));
    }
    return descripcionProducto;
  }

  @Override
  public Transportista getTransportistaPorNombre(String nombre, Empresa empresa) {
    return transportistaRepository.findByNombreAndEmpresaAndEliminado(nombre, empresa, false);
  }

  private void validarOperacion(TipoDeOperacion operacion, Transportista transportista) {
    // Duplicados
    // Nombre
    Transportista transportistaDuplicado =
        this.getTransportistaPorNombre(transportista.getNombre(), transportista.getEmpresa());
    if (operacion.equals(TipoDeOperacion.ALTA) && transportistaDuplicado != null) {
      throw new BusinessServiceException(
          RESOURCE_BUNDLE.getString("mensaje_transportista_duplicado_nombre"));
    }
    if (operacion.equals(TipoDeOperacion.ACTUALIZACION)
        && (transportistaDuplicado != null
            && transportistaDuplicado.getId_Transportista()
                != transportista.getId_Transportista())) {
      throw new BusinessServiceException(
          RESOURCE_BUNDLE.getString("mensaje_transportista_duplicado_nombre"));
    }
    if (transportista.getUbicacion() != null
        && transportista.getUbicacion().getLocalidad() == null) {
      throw new BusinessServiceException(
          RESOURCE_BUNDLE.getString("mensaje_ubicacion_sin_localidad"));
    }
  }

  @Override
  @Transactional
  public Transportista guardar(@Valid Transportista transportista) {
    if (transportista.getUbicacion() != null
        && transportista.getUbicacion().getIdLocalidad() != null) {
      transportista
          .getUbicacion()
          .setLocalidad(
              ubicacionService.getLocalidadPorId(transportista.getUbicacion().getIdLocalidad()));
    }
    this.validarOperacion(TipoDeOperacion.ALTA, transportista);
    transportista = transportistaRepository.save(transportista);
    logger.warn("El Transportista {} se guardó correctamente.", transportista);
    return transportista;
  }

  @Override
  @Transactional
  public void actualizar(@Valid Transportista transportista) {
    this.validarOperacion(TipoDeOperacion.ACTUALIZACION, transportista);
    transportistaRepository.save(transportista);
  }

  @Override
  @Transactional
  public void eliminar(long idTransportista) {
    Transportista transportista = this.getTransportistaNoEliminadoPorId(idTransportista);
    if (transportista == null) {
      throw new EntityNotFoundException(
          ResourceBundle.getBundle("Mensajes").getString("mensaje_transportista_no_existente"));
    }
    transportista.setEliminado(true);
    transportista.setUbicacion(null);
    transportistaRepository.save(transportista);
  }
}
