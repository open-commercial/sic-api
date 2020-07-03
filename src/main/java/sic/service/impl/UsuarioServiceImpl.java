package sic.service.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.persistence.EntityNotFoundException;

import com.querydsl.core.BooleanBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sic.modelo.*;
import sic.modelo.criteria.BusquedaUsuarioCriteria;
import sic.service.*;
import sic.repository.UsuarioRepository;
import sic.exception.BusinessServiceException;
import sic.exception.ServiceException;
import sic.util.CustomValidator;

@Service
@Transactional
public class UsuarioServiceImpl implements IUsuarioService {

  private final UsuarioRepository usuarioRepository;
  private final ISucursalService sucursalService;
  private final IClienteService clienteService;
  private final ICorreoElectronicoService correoElectronicoService;
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private static final int TAMANIO_PAGINA_DEFAULT = 50;
  private final MessageSource messageSource;
  private final CustomValidator customValidator;

  @Autowired
  @Lazy
  public UsuarioServiceImpl(
    UsuarioRepository usuarioRepository,
    ISucursalService sucursalService,
    IClienteService clienteService,
    ICorreoElectronicoService correoElectronicoService,
    MessageSource messageSource,
    CustomValidator customValidator) {
    this.usuarioRepository = usuarioRepository;
    this.sucursalService = sucursalService;
    this.clienteService = clienteService;
    this.correoElectronicoService = correoElectronicoService;
    this.messageSource = messageSource;
    this.customValidator = customValidator;
  }

  public String encriptarConMD5(String password) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] array = md.digest(password.getBytes());
      StringBuilder sb = new StringBuilder();
      for (byte anArray : array) {
        sb.append(Integer.toHexString((anArray & 0xFF) | 0x100).substring(1, 3));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new ServiceException(
          messageSource.getMessage("mensaje_encriptacion_no_disponible", null, Locale.getDefault()),
          ex);
    }
  }

  @Override
  public Usuario getUsuarioNoEliminadoPorId(Long idUsuario) {
    Optional<Usuario> usuario = usuarioRepository
      .findByIdUsuario(idUsuario);
    if (usuario.isPresent() && !usuario.get().isEliminado()) {
      return usuario.get();
    } else {
      throw new EntityNotFoundException(messageSource.getMessage(
        "mensaje_usuario_no_existente", null, Locale.getDefault()));
    }
  }

  @Override
  public Usuario getUsuarioPorUsername(String username) {
    return this.usuarioRepository.findByUsernameAndEliminado(username, false);
  }

  @Override
  public Usuario getUsuarioPorPasswordRecoveryKeyAndIdUsuario(
      String passwordRecoveryKey, long idUsuario) {
    return usuarioRepository.findByPasswordRecoveryKeyAndIdUsuarioAndEliminadoAndHabilitado(
        passwordRecoveryKey, idUsuario);
  }

  @Override
  public Usuario autenticarUsuario(Credencial credencial) {
    Usuario usuario =
        usuarioRepository.findByUsernameOrEmailAndPasswordAndEliminado(
            credencial.getUsername(),
            credencial.getUsername(),
            this.encriptarConMD5(credencial.getPassword()));
    if (usuario == null) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_usuario_logInInvalido", null, Locale.getDefault()));
    }
    if (credencial.getAplicacion() == null) {
      throw new BusinessServiceException(
          messageSource.getMessage("mensaje_usuario_logInInvalido_aplicacion", null, Locale.getDefault()));
    }
    if (!usuario.isHabilitado()) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_usuario_no_habilitado", null, Locale.getDefault()));
    }
    return usuario;
  }

  @Override
  public Page<Usuario> buscarUsuarios(BusquedaUsuarioCriteria criteria) {
    QUsuario qUsuario = QUsuario.usuario;
    BooleanBuilder builder = new BooleanBuilder();
    if (criteria.getApellido() != null) {
      String[] terminos = criteria.getApellido().split(" ");
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (String termino : terminos) {
        rsPredicate.and(qUsuario.apellido.containsIgnoreCase(termino));
      }
      builder.or(rsPredicate);
    }
    if (criteria.getNombre() != null) {
      String[] terminos = criteria.getNombre().split(" ");
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (String termino : terminos) {
        rsPredicate.and(qUsuario.nombre.containsIgnoreCase(termino));
      }
      builder.or(rsPredicate);
    }
    if (criteria.getUsername() != null) {
      String[] terminos = criteria.getUsername().split(" ");
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (String termino : terminos) {
        rsPredicate.and(qUsuario.username.containsIgnoreCase(termino));
      }
      builder.or(rsPredicate);
    }
    if (criteria.getEmail() != null) {
      String[] terminos = criteria.getEmail().split(" ");
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (String termino : terminos) {
        rsPredicate.and(qUsuario.email.containsIgnoreCase(termino));
      }
      builder.or(rsPredicate);
    }
    if (criteria.getRoles() != null && !criteria.getRoles().isEmpty()) {
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (Rol rol : criteria.getRoles()) {
        switch (rol) {
          case ADMINISTRADOR:
            rsPredicate.or(qUsuario.roles.contains(Rol.ADMINISTRADOR));
            break;
          case ENCARGADO:
            rsPredicate.or(qUsuario.roles.contains(Rol.ENCARGADO));
            break;
          case VENDEDOR:
            rsPredicate.or(qUsuario.roles.contains(Rol.VENDEDOR));
            break;
          case VIAJANTE:
            rsPredicate.or(qUsuario.roles.contains(Rol.VIAJANTE));
            break;
          case COMPRADOR:
            rsPredicate.or(qUsuario.roles.contains(Rol.COMPRADOR));
            break;
        }
      }
      builder.and(rsPredicate);
    }
    builder.and(qUsuario.eliminado.eq(false));
    return usuarioRepository.findAll(builder, this.getPageable(criteria.getPagina(), criteria.getOrdenarPor(), criteria.getSentido()));
  }

  private Pageable getPageable(Integer pagina, String ordenarPor, String sentido) {
    String ordenDefault = "username";
    if (pagina == null) pagina = 0;
    if (ordenarPor == null || sentido == null) {
      return PageRequest.of(
          pagina, TAMANIO_PAGINA_DEFAULT, Sort.by(Sort.Direction.ASC, ordenDefault));
    } else {
      switch (sentido) {
        case "ASC":
          return PageRequest.of(
              pagina, TAMANIO_PAGINA_DEFAULT, Sort.by(Sort.Direction.ASC, ordenarPor));
        case "DESC":
          return PageRequest.of(
              pagina, TAMANIO_PAGINA_DEFAULT, Sort.by(Sort.Direction.DESC, ordenarPor));
        default:
          return PageRequest.of(
              pagina, TAMANIO_PAGINA_DEFAULT, Sort.by(Sort.Direction.DESC, ordenDefault));
      }
    }
  }

  @Override
  public void validarReglasDeNegocio(TipoDeOperacion operacion, Usuario usuario) {
    // Username sin espacios en blanco
    if (usuario.getUsername().contains(" ")) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_usuario_username_con_espacios", null, Locale.getDefault()));
    }
    // Duplicados
    if (operacion == TipoDeOperacion.ALTA) {
      // username
      if (usuarioRepository.findByUsernameAndEliminado(usuario.getUsername(), false) != null) {
        throw new BusinessServiceException(messageSource.getMessage(
          "mensaje_usuario_duplicado_username", null, Locale.getDefault()));
      }
      // email
      if (usuarioRepository.findByEmailAndEliminado(usuario.getEmail(), false) != null) {
        throw new BusinessServiceException(messageSource.getMessage(
          "mensaje_usuario_duplicado_email", null, Locale.getDefault()));
      }
    }
    if (operacion == TipoDeOperacion.ACTUALIZACION) {
      // username
      Usuario usuarioGuardado =
          usuarioRepository.findByUsernameAndEliminado(usuario.getUsername(), false);
      if (usuarioGuardado != null && usuarioGuardado.getIdUsuario() != usuario.getIdUsuario()) {
        throw new BusinessServiceException(messageSource.getMessage(
          "mensaje_usuario_duplicado_username", null, Locale.getDefault()));
      }
      // email
      usuarioGuardado = usuarioRepository.findByEmailAndEliminado(usuario.getEmail(), false);
      if (usuarioGuardado != null && usuarioGuardado.getIdUsuario() != usuario.getIdUsuario()) {
        throw new BusinessServiceException(messageSource.getMessage(
          "mensaje_usuario_duplicado_email", null, Locale.getDefault()));
      }
    }
    // Ultimo usuario administrador
    if ((operacion == TipoDeOperacion.ACTUALIZACION
            && !usuario.getRoles().contains(Rol.ADMINISTRADOR))
        || operacion == TipoDeOperacion.ELIMINACION
            && usuario.getRoles().contains(Rol.ADMINISTRADOR)) {
      List<Usuario> administradores = this.getUsuariosPorRol(Rol.ADMINISTRADOR).getContent();
      if (administradores.size() == 1
          && administradores.get(0).getIdUsuario() == usuario.getIdUsuario()) {
        throw new BusinessServiceException(messageSource.getMessage(
          "mensaje_usuario_ultimoAdmin", null, Locale.getDefault()));
      }
    }
  }

  @Override
  public Page<Usuario> getUsuariosPorRol(Rol rol) {
    Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
    return usuarioRepository.findAllByRolesContainsAndEliminado(rol, false, pageable);
  }

  @Override
  @Transactional
  public void actualizar(Usuario usuario) {
    customValidator.validar(usuario);
    this.validarReglasDeNegocio(TipoDeOperacion.ACTUALIZACION, usuario);
    if (!usuario.getRoles().contains(Rol.VIAJANTE)) {
      this.clienteService.desvincularClienteDeViajante(usuario.getIdUsuario());
    }
    if (!usuario.getRoles().contains(Rol.COMPRADOR)) {
      this.clienteService.desvincularClienteDeCredencial(usuario.getIdUsuario());
    }
    usuario.setUsername(usuario.getUsername().toLowerCase());
    usuarioRepository.save(usuario);
    logger.warn("El Usuario {} se actualizó correctamente.", usuario);
  }

  @Override
  public void actualizarPasswordRecoveryKey(String passwordRecoveryKey, long idUsuario) {
    usuarioRepository.updatePasswordRecoveryKey(
        passwordRecoveryKey, LocalDateTime.now().plusHours(3L), idUsuario);
  }

  @Override
  public void actualizarIdSucursalDeUsuario(long idUsuario, long idSucursalPredeterminada) {
    if (sucursalService.getSucursalPorId(idSucursalPredeterminada) == null) {
      throw new EntityNotFoundException(messageSource.getMessage(
        "mensaje_sucursal_no_existente", null, Locale.getDefault()));
    }
    usuarioRepository.updateIdSucursal(idUsuario, idSucursalPredeterminada);
  }

  @Override
  public void enviarEmailDeRecuperacion(long idSucursal, String email, String host) {
    Usuario usuario = usuarioRepository.findByEmailAndEliminado(email, false);
    if (usuario == null || !usuario.isHabilitado()) {
      throw new BusinessServiceException(
          messageSource.getMessage(
              "mensaje_correo_no_existente_or_deshabilitado", null, Locale.getDefault()));
    }
    String passwordRecoveryKey = RandomStringUtils.random(250, true, true);
    this.actualizarPasswordRecoveryKey(passwordRecoveryKey, usuario.getIdUsuario());
    correoElectronicoService.enviarEmail(
        usuario.getEmail(),
        "",
        "Recuperación de contraseña",
        messageSource.getMessage(
            "mensaje_correo_recuperacion",
            new Object[] {host, passwordRecoveryKey, usuario.getIdUsuario()},
            Locale.getDefault()),
        null,
        null);
  }

  @Override
  @Transactional
  public Usuario guardar(Usuario usuario) {
    customValidator.validar(usuario);
    this.validarReglasDeNegocio(TipoDeOperacion.ALTA, usuario);
    usuario.setUsername(usuario.getUsername().toLowerCase());
    usuario.setPassword(this.encriptarConMD5(usuario.getPassword()));
    usuario = usuarioRepository.save(usuario);
    logger.warn("El Usuario {} se guardó correctamente.", usuario);
    return usuario;
  }

  @Override
  public void eliminar(long idUsuario) {
    Usuario usuario = this.getUsuarioNoEliminadoPorId(idUsuario);
    this.validarReglasDeNegocio(TipoDeOperacion.ELIMINACION, usuario);
    Cliente clienteVinculado = clienteService.getClientePorCredencial(usuario);
    if (clienteVinculado != null)
      throw new BusinessServiceException(
          messageSource.getMessage(
              "mensaje_usuario_eliminar_con_credencial",
              new Object[] {clienteVinculado.getNombreFiscal()},
              Locale.getDefault()));
    usuario.setEliminado(true);
    usuarioRepository.save(usuario);
    logger.warn("El Usuario {} se eliminó correctamente.", usuario);
  }
}
