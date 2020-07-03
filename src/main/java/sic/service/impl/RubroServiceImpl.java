package sic.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sic.modelo.Rubro;
import sic.service.IRubroService;
import sic.exception.BusinessServiceException;
import sic.modelo.TipoDeOperacion;
import sic.repository.RubroRepository;
import sic.util.CustomValidator;

@Service
public class RubroServiceImpl implements IRubroService {

  private final RubroRepository rubroRepository;
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final MessageSource messageSource;
  private final CustomValidator customValidator;

  @Autowired
  public RubroServiceImpl(
    RubroRepository rubroRepository,
    MessageSource messageSource,
    CustomValidator customValidator) {
    this.rubroRepository = rubroRepository;
    this.messageSource = messageSource;
    this.customValidator = customValidator;
  }

  @Override
  public Rubro getRubroNoEliminadoPorId(Long idRubro) {
    Optional<Rubro> rubro = rubroRepository
      .findById(idRubro);
    if (rubro.isPresent() && !rubro.get().isEliminado()) {
      return rubro.get();
    } else {
      throw new EntityNotFoundException(messageSource.getMessage(
        "mensaje_rubro_no_existente", null, Locale.getDefault()));
    }
  }

  @Override
  public List<Rubro> getRubros() {
    return rubroRepository.findAllByAndEliminadoOrderByNombreAsc(false);
  }

  @Override
  public Rubro getRubroPorNombre(String nombre) {
    return rubroRepository.findByNombreAndEliminado(nombre, false);
  }

  @Override
  public void validarReglasDeNegocio(TipoDeOperacion operacion, Rubro rubro) {
    // Duplicados
    // Nombre
    Rubro rubroDuplicado = this.getRubroPorNombre(rubro.getNombre());
    if (operacion.equals(TipoDeOperacion.ALTA) && rubroDuplicado != null) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_rubro_nombre_duplicado", null, Locale.getDefault()));
    }
    if (operacion.equals(TipoDeOperacion.ACTUALIZACION)
        && (rubroDuplicado != null && rubroDuplicado.getIdRubro() != rubro.getIdRubro())) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_rubro_nombre_duplicado", null, Locale.getDefault()));
    }
  }

  @Override
  @Transactional
  public void actualizar(Rubro rubro) {
    customValidator.validar(rubro);
    this.validarReglasDeNegocio(TipoDeOperacion.ACTUALIZACION, rubro);
    rubroRepository.save(rubro);
  }

  @Override
  @Transactional
  public Rubro guardar(Rubro rubro) {
    customValidator.validar(rubro);
    this.validarReglasDeNegocio(TipoDeOperacion.ALTA, rubro);
    rubro = rubroRepository.save(rubro);
    logger.warn("El Rubro {} se guardó correctamente.", rubro);
    return rubro;
  }

  @Override
  @Transactional
  public void eliminar(long idRubro) {
    Rubro rubro = this.getRubroNoEliminadoPorId(idRubro);
    if (rubro == null) {
      throw new EntityNotFoundException(messageSource.getMessage(
        "mensaje_pedido_no_existente", null, Locale.getDefault()));
    }
    rubro.setEliminado(true);
    rubroRepository.save(rubro);
  }
}
