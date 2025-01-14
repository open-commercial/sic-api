package org.opencommercial.service;

import com.querydsl.core.BooleanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.opencommercial.exception.BusinessServiceException;
import org.opencommercial.exception.ServiceException;
import org.opencommercial.model.*;
import org.opencommercial.model.criteria.BusquedaCuentaCorrienteClienteCriteria;
import org.opencommercial.model.criteria.BusquedaCuentaCorrienteProveedorCriteria;
import org.opencommercial.repository.CuentaCorrienteClienteRepository;
import org.opencommercial.repository.CuentaCorrienteProveedorRepository;
import org.opencommercial.repository.CuentaCorrienteRepository;
import org.opencommercial.repository.RenglonCuentaCorrienteRepository;
import org.opencommercial.util.CustomValidator;
import org.opencommercial.util.FormatoReporte;
import org.opencommercial.util.JasperReportsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class CuentaCorrienteServiceImpl implements CuentaCorrienteService {

  private final CuentaCorrienteRepository<CuentaCorriente> cuentaCorrienteRepository;
  private final CuentaCorrienteClienteRepository cuentaCorrienteClienteRepository;
  private final CuentaCorrienteProveedorRepository cuentaCorrienteProveedorRepository;
  private final RenglonCuentaCorrienteRepository renglonCuentaCorrienteRepository;
  private final UsuarioService usuarioService;
  private final ClienteService clienteService;
  private final SucursalService sucursalService;
  private static final int TAMANIO_PAGINA_DEFAULT = 25;
  private final MessageSource messageSource;
  private final CustomValidator customValidator;
  private final JasperReportsHandler jasperReportsHandler;
  private static final String MENSAJE_RENGLON_CC_GUARDADO = "mensaje_renglon_cuenta_corriente_guardado";
  private static final String MENSAJE_RENGLON_CC_ELIMINADO = "mensaje_renglon_cuenta_corriente_eliminado";
  private static final String MENSAJE_CC_NO_EXISTENTE = "mensaje_cuenta_corriente_no_existente";

  @Autowired
  @Lazy
  public CuentaCorrienteServiceImpl(
          CuentaCorrienteRepository<CuentaCorriente> cuentaCorrienteRepository,
          CuentaCorrienteClienteRepository cuentaCorrienteClienteRepository,
          CuentaCorrienteProveedorRepository cuentaCorrienteProveedorRepository,
          RenglonCuentaCorrienteRepository renglonCuentaCorrienteRepository,
          UsuarioService usuarioService, ClienteService clienteService,
          SucursalService sucursalService, CustomValidator customValidator,
          MessageSource messageSource, JasperReportsHandler jasperReportsHandler) {
    this.cuentaCorrienteRepository = cuentaCorrienteRepository;
    this.cuentaCorrienteClienteRepository = cuentaCorrienteClienteRepository;
    this.cuentaCorrienteProveedorRepository = cuentaCorrienteProveedorRepository;
    this.renglonCuentaCorrienteRepository = renglonCuentaCorrienteRepository;
    this.usuarioService = usuarioService;
    this.clienteService = clienteService;
    this.sucursalService = sucursalService;
    this.messageSource = messageSource;
    this.customValidator = customValidator;
    this.jasperReportsHandler = jasperReportsHandler;
  }

  @Override
  @Transactional
  public CuentaCorrienteCliente guardarCuentaCorrienteCliente(CuentaCorrienteCliente cuentaCorrienteCliente) {
    customValidator.validar(cuentaCorrienteCliente);
    this.validarReglasDeNegocio(cuentaCorrienteCliente);
    cuentaCorrienteCliente = cuentaCorrienteClienteRepository.save(cuentaCorrienteCliente);
    log.info("La cuenta corriente cliente se guardó correctamente. {}", cuentaCorrienteCliente);
    return cuentaCorrienteCliente;
  }

  @Override
  @Transactional
  public CuentaCorrienteProveedor guardarCuentaCorrienteProveedor(CuentaCorrienteProveedor cuentaCorrienteProveedor) {
    customValidator.validar(cuentaCorrienteProveedor);
    this.validarReglasDeNegocio(cuentaCorrienteProveedor);
    cuentaCorrienteProveedor = cuentaCorrienteProveedorRepository.save(cuentaCorrienteProveedor);
    log.info("La cuenta corriente proveedor se guardó correctamente. {}", cuentaCorrienteProveedor);
    return cuentaCorrienteProveedor;
  }

  @Override
  public BigDecimal getSaldoCuentaCorriente(long idCliente) {
    return cuentaCorrienteClienteRepository.getSaldoCuentaCorrienteCliente(idCliente);
  }

  @Override
  public void validarReglasDeNegocio(CuentaCorriente cuentaCorriente) {
    // Duplicados
    if (cuentaCorriente.getIdCuentaCorriente() != null
        && cuentaCorrienteRepository.findById(cuentaCorriente.getIdCuentaCorriente()).isPresent()) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_cuenta_corriente_duplicada", null, Locale.getDefault()));
    }
  }

  @Override
  public void eliminarCuentaCorrienteCliente(long idCliente) {
    cuentaCorrienteClienteRepository.eliminarCuentaCorrienteCliente(idCliente);
  }

  @Override
  public void eliminarCuentaCorrienteProveedor(long idProveedor) {
    cuentaCorrienteProveedorRepository.eliminarCuentaCorrienteProveedor(idProveedor);
  }

  @Override
  public Page<CuentaCorrienteCliente> buscarCuentaCorrienteCliente(BusquedaCuentaCorrienteClienteCriteria criteria,
                                                                   long idUsuarioLoggedIn) {
    return cuentaCorrienteClienteRepository.findAll(
        this.getBuilder(criteria, idUsuarioLoggedIn),
        this.getPageable(
            criteria.getPagina(),
            criteria.getOrdenarPor(),
            criteria.getSentido(),
            "cliente.nombreFiscal",
            TAMANIO_PAGINA_DEFAULT));
  }

  @Override
  public List<CuentaCorrienteCliente> buscarCuentasCorrienteClienteParaReporte(BusquedaCuentaCorrienteClienteCriteria criteria,
                                                                               long idUsuarioLoggedIn) {
    criteria.setPagina(0);
    return cuentaCorrienteClienteRepository.findAll(
            this.getBuilder(criteria, idUsuarioLoggedIn),
            this.getPageable(
                criteria.getPagina(),
                criteria.getOrdenarPor(),
                criteria.getSentido(),
                "cliente.nombreFiscal",
                Integer.MAX_VALUE))
        .getContent();
  }

  @Override
  public BooleanBuilder getBuilder(BusquedaCuentaCorrienteClienteCriteria criteria, long idUsuarioLoggedIn) {
    QCuentaCorrienteCliente qCuentaCorrienteCliente = QCuentaCorrienteCliente.cuentaCorrienteCliente;
    BooleanBuilder builder = new BooleanBuilder();
    if (criteria.getNombreFiscal() != null) {
      String[] terminos = criteria.getNombreFiscal().split(" ");
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (String termino : terminos) {
        rsPredicate.and(qCuentaCorrienteCliente.cliente.nombreFiscal.containsIgnoreCase(termino));
      }
      builder.or(rsPredicate);
    }
    if (criteria.getNombreFantasia() != null) {
      String[] terminos = criteria.getNombreFantasia().split(" ");
      BooleanBuilder nfPredicate = new BooleanBuilder();
      for (String termino : terminos) {
        nfPredicate.and(qCuentaCorrienteCliente.cliente.nombreFantasia.containsIgnoreCase(termino));
      }
      builder.or(nfPredicate);
    }
    if (criteria.getIdFiscal() != null && criteria.getIdFiscal().matches("\\d+"))
      builder.or(qCuentaCorrienteCliente.cliente.idFiscal.eq(Long.valueOf(criteria.getIdFiscal())));
    if (criteria.getNroDeCliente() != null)
      builder.or(qCuentaCorrienteCliente.cliente.nroCliente.containsIgnoreCase(criteria.getNroDeCliente()));
    if (criteria.getIdViajante() != null)
      builder.and(qCuentaCorrienteCliente.cliente.viajante.idUsuario.eq(criteria.getIdViajante()));
    if (criteria.getIdLocalidad() != null)
      builder.and(qCuentaCorrienteCliente.cliente.ubicacionFacturacion.localidad.idLocalidad.eq(criteria.getIdLocalidad()));
    if (criteria.getIdProvincia() != null)
      builder.and(qCuentaCorrienteCliente.cliente.ubicacionFacturacion.localidad.provincia.idProvincia.eq(criteria.getIdProvincia()));
    Usuario usuarioLoggedIn = usuarioService.getUsuarioNoEliminadoPorId(idUsuarioLoggedIn);
    if (!usuarioLoggedIn.getRoles().contains(Rol.ADMINISTRADOR)
            && !usuarioLoggedIn.getRoles().contains(Rol.VENDEDOR)
            && !usuarioLoggedIn.getRoles().contains(Rol.ENCARGADO)) {
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (Rol rol : usuarioLoggedIn.getRoles()) {
        switch (rol) {
          case VIAJANTE -> rsPredicate.or(qCuentaCorrienteCliente.cliente.viajante.eq(usuarioLoggedIn));
          case COMPRADOR -> this.filtraPorClienteRelacionado(rsPredicate, idUsuarioLoggedIn, qCuentaCorrienteCliente);
          default -> rsPredicate.or(qCuentaCorrienteCliente.cliente.isNull());
        }
      }
      builder.and(rsPredicate);
    }
    builder.and(qCuentaCorrienteCliente.eliminada.eq(false));
    return builder;
  }

  private void filtraPorClienteRelacionado(BooleanBuilder rsPredicate, long idUsuarioLoggedIn,
                                           QCuentaCorrienteCliente qCuentaCorrienteCliente) {
    Cliente clienteRelacionado = clienteService.getClientePorIdUsuario(idUsuarioLoggedIn);
    if (clienteRelacionado != null) {
      rsPredicate.or(qCuentaCorrienteCliente.cliente.eq(clienteRelacionado));
    }
  }

  @Override
  public Pageable getPageable(Integer pagina, String ordenarPor, String sentido, String ordenDefault, int tamanioPagina) {
    if (pagina == null)
      pagina = 0;
    if (ordenarPor == null || sentido == null) {
      return PageRequest.of(pagina, tamanioPagina, Sort.by(Sort.Direction.DESC, ordenDefault));
    } else {
      return switch (sentido) {
        case "ASC" -> PageRequest.of(pagina, tamanioPagina, Sort.by(Sort.Direction.ASC, ordenarPor));
        case "DESC" -> PageRequest.of(pagina, tamanioPagina, Sort.by(Sort.Direction.DESC, ordenarPor));
        default -> PageRequest.of(pagina, tamanioPagina, Sort.by(Sort.Direction.DESC, ordenDefault));
      };
    }
  }

  @Override
  public Page<CuentaCorrienteProveedor> buscarCuentaCorrienteProveedor(BusquedaCuentaCorrienteProveedorCriteria criteria) {
    QCuentaCorrienteProveedor qCuentaCorrienteProveedor = QCuentaCorrienteProveedor.cuentaCorrienteProveedor;
    BooleanBuilder builder = new BooleanBuilder();
    if (criteria.getNroProveedor() != null)
      builder.or(qCuentaCorrienteProveedor.proveedor.nroProveedor.containsIgnoreCase(criteria.getNroProveedor()));
    if (criteria.getRazonSocial() != null) {
      String[] terminos = criteria.getRazonSocial().split(" ");
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (String termino : terminos) {
        rsPredicate.and(qCuentaCorrienteProveedor.proveedor.razonSocial.containsIgnoreCase(termino));
      }
      builder.or(rsPredicate);
    }
    if (criteria.getIdFiscal() != null && criteria.getIdFiscal().matches("\\d+"))
      builder.or(qCuentaCorrienteProveedor.proveedor.idFiscal.eq(Long.valueOf(criteria.getIdFiscal())));
    if (criteria.getIdLocalidad() != null)
      builder.and(qCuentaCorrienteProveedor.proveedor.ubicacion.localidad.idLocalidad.eq(criteria.getIdLocalidad()));
    if (criteria.getIdProvincia() != null)
      builder.and(qCuentaCorrienteProveedor.proveedor.ubicacion.localidad.provincia.idProvincia.eq(criteria.getIdProvincia()));
    builder.and(qCuentaCorrienteProveedor.eliminada.eq(false));
    return cuentaCorrienteProveedorRepository
            .findAll(builder,
                    this.getPageable(criteria.getPagina(),
                            criteria.getOrdenarPor(),
                            criteria.getSentido(),
                            "proveedor.razonSocial",
                            TAMANIO_PAGINA_DEFAULT));
  }

  @Override
  public CuentaCorrienteCliente getCuentaCorrientePorCliente(Cliente cliente) {
    return cuentaCorrienteClienteRepository.findByClienteAndEliminada(cliente, false);
  }

  @Override
  public CuentaCorrienteProveedor getCuentaCorrientePorProveedor(Proveedor proveedor) {
    return cuentaCorrienteProveedorRepository.findByProveedorAndEliminada(proveedor, false);
  }

  @Override
  @Transactional
  public void asentarEnCuentaCorriente(FacturaVenta facturaVenta, TipoDeOperacion tipo) {
    var cc = this.getCuentaCorrientePorCliente(clienteService.getClienteNoEliminadoPorId(facturaVenta.getIdCliente()));
    if (null == cc) {
      throw new BusinessServiceException(messageSource.getMessage(MENSAJE_CC_NO_EXISTENTE, null, Locale.getDefault()));
    }
    cc.setFechaUltimoMovimiento(LocalDateTime.now());
    if (tipo == TipoDeOperacion.ALTA) {
      this.guardarRenglonCuentaCorrienteDeFactura(facturaVenta, cc);
    }
    if (tipo == TipoDeOperacion.ELIMINACION) {
      this.setSaldoCuentaCorriente(cc, cc.getSaldo().add(facturaVenta.getTotal()));
      RenglonCuentaCorriente rcc = this.getRenglonCuentaCorrienteDeFactura(facturaVenta, false);
      this.cambiarFechaUltimoComprobante(cc, rcc);
      rcc.setEliminado(true);
      log.info(messageSource.getMessage(MENSAJE_RENGLON_CC_ELIMINADO, null, Locale.getDefault()), rcc);
    }
  }

  @Override
  @Transactional
  public void asentarEnCuentaCorriente(FacturaCompra facturaCompra) {
    CuentaCorriente cc = this.getCuentaCorrientePorProveedor(facturaCompra.getProveedor());
    if (null == cc) {
      throw new BusinessServiceException(messageSource.getMessage(MENSAJE_CC_NO_EXISTENTE, null, Locale.getDefault()));
    }
    cc.setFechaUltimoMovimiento(LocalDateTime.now());
    this.guardarRenglonCuentaCorrienteDeFactura(facturaCompra, cc);
  }

  private void guardarRenglonCuentaCorrienteDeFactura(Factura factura, CuentaCorriente cc) {
    RenglonCuentaCorriente rcc = new RenglonCuentaCorriente();
    rcc.setTipoComprobante(factura.getTipoComprobante());
    rcc.setSerie(factura.getNumSerie());
    rcc.setNumero(factura.getNumFactura());
    rcc.setFactura(factura);
    rcc.setFecha(factura.getFecha());
    rcc.setIdMovimiento(factura.getIdFactura());
    rcc.setMonto(factura.getTotal().negate());
    cc.getRenglones().add(rcc);
    this.setSaldoCuentaCorriente(cc, cc.getSaldo().add(rcc.getMonto()));
    cc.setFechaUltimoMovimiento(factura.getFecha());
    rcc.setCuentaCorriente(cc);
    this.renglonCuentaCorrienteRepository.save(rcc);
    log.info(messageSource.getMessage(MENSAJE_RENGLON_CC_GUARDADO, null, Locale.getDefault()), rcc);
  }

  @Override
  @Transactional
  public void asentarEnCuentaCorriente(Nota nota, TipoDeOperacion tipo) {
    CuentaCorriente cc = this.getCuentaCorrientePorNota(nota);
    cc.setFechaUltimoMovimiento(LocalDateTime.now());
    if (tipo == TipoDeOperacion.ALTA) {
      RenglonCuentaCorriente rcc = new RenglonCuentaCorriente();
      rcc.setTipoComprobante(nota.getTipoComprobante());
      rcc.setSerie(nota.getSerie());
      rcc.setNumero(nota.getNroNota());
      if (nota instanceof NotaCredito) {
        rcc.setMonto(nota.getTotal());
      }
      if (nota instanceof NotaDebito) {
        rcc.setMonto(nota.getTotal().negate());
      }
      this.setSaldoCuentaCorriente(cc, cc.getSaldo().add(rcc.getMonto()));
      cc.setFechaUltimoMovimiento(nota.getFecha());
      rcc.setDescripcion(nota.getMotivo());
      rcc.setNota(nota);
      rcc.setFecha(nota.getFecha());
      rcc.setIdMovimiento(nota.getIdNota());
      cc.getRenglones().add(rcc);
      rcc.setCuentaCorriente(cc);
      this.renglonCuentaCorrienteRepository.save(rcc);
      log.info(messageSource.getMessage(MENSAJE_RENGLON_CC_GUARDADO, null, Locale.getDefault()), rcc);
    }
    if (tipo == TipoDeOperacion.ELIMINACION) {
      RenglonCuentaCorriente rcc = this.getRenglonCuentaCorrienteDeNota(nota, false);
      this.setSaldoCuentaCorriente(cc, cc.getSaldo().subtract(rcc.getMonto()));
      this.cambiarFechaUltimoComprobante(cc, rcc);
      rcc.setEliminado(true);
      log.info(messageSource.getMessage(MENSAJE_RENGLON_CC_ELIMINADO, null, Locale.getDefault()), rcc);
    }
  }

  private CuentaCorriente getCuentaCorrientePorNota(Nota nota) {
    if (nota.getMovimiento().equals(Movimiento.VENTA)) {
      return this.getCuentaCorrientePorCliente(
          nota.getCliente());
    } else {
      return this.getCuentaCorrientePorProveedor(nota.getProveedor());
    }
  }

  @Override
  @Transactional
  public void asentarEnCuentaCorriente(Remito remito, TipoDeOperacion tipo) {
    CuentaCorriente cc = this.getCuentaCorrientePorCliente(remito.getCliente());
    if (tipo == TipoDeOperacion.ALTA) {
      RenglonCuentaCorriente rcc = new RenglonCuentaCorriente();
      rcc.setSerie(remito.getSerie());
      rcc.setNumero(remito.getNroRemito());
      rcc.setMonto(remito.getCostoDeEnvio().negate());
      this.setSaldoCuentaCorriente(cc, cc.getSaldo().add(rcc.getMonto()));
      cc.setFechaUltimoMovimiento(remito.getFecha());
      rcc.setRemito(remito);
      rcc.setTipoComprobante(TipoDeComprobante.REMITO);
      rcc.setFecha(remito.getFecha());
      rcc.setIdMovimiento(remito.getIdRemito());
      cc.getRenglones().add(rcc);
      rcc.setCuentaCorriente(cc);
      this.renglonCuentaCorrienteRepository.save(rcc);
      log.info(messageSource.getMessage(MENSAJE_RENGLON_CC_GUARDADO, null, Locale.getDefault()), rcc);
    }
    if (tipo == TipoDeOperacion.ELIMINACION) {
      RenglonCuentaCorriente rcc = this.getRenglonCuentaCorrienteDeRemito(remito, false);
      this.setSaldoCuentaCorriente(cc, cc.getSaldo().subtract(rcc.getMonto()));
      this.cambiarFechaUltimoComprobante(cc, rcc);
      rcc.setEliminado(true);
      this.renglonCuentaCorrienteRepository.save(rcc);
      log.info(messageSource.getMessage(MENSAJE_RENGLON_CC_ELIMINADO, null, Locale.getDefault()), rcc);
    }
  }

  @Override
  @Transactional
  public void asentarEnCuentaCorriente(Recibo recibo, TipoDeOperacion tipo) {
    RenglonCuentaCorriente rcc;
    if (tipo == TipoDeOperacion.ALTA) {
      rcc = new RenglonCuentaCorriente();
      rcc.setRecibo(recibo);
      rcc.setTipoComprobante(TipoDeComprobante.RECIBO);
      rcc.setSerie(recibo.getNumSerie());
      rcc.setNumero(recibo.getNumRecibo());
      rcc.setDescripcion(recibo.getConcepto());
      rcc.setFecha(recibo.getFecha());
      rcc.setIdMovimiento(recibo.getIdRecibo());
      rcc.setMonto(recibo.getMonto());
      CuentaCorriente cc = null;
      if (recibo.getCliente() != null) {
        cc = this.getCuentaCorrientePorCliente(recibo.getCliente());
      } else if (recibo.getProveedor() != null) {
        cc = this.getCuentaCorrientePorProveedor(recibo.getProveedor());
      }
      if (cc == null) {
        throw new BusinessServiceException(messageSource.getMessage(MENSAJE_CC_NO_EXISTENTE, null, Locale.getDefault()));
      }
      cc.getRenglones().add(rcc);
      this.setSaldoCuentaCorriente(cc, cc.getSaldo().add(recibo.getMonto()));
      cc.setFechaUltimoMovimiento(recibo.getFecha());
      rcc.setCuentaCorriente(cc);
      this.renglonCuentaCorrienteRepository.save(rcc);
      log.info(messageSource.getMessage(MENSAJE_RENGLON_CC_GUARDADO, null, Locale.getDefault()), rcc);
    }
    if (tipo == TipoDeOperacion.ELIMINACION) {
      CuentaCorriente cc = null;
      if (recibo.getCliente() != null) {
        cc = this.getCuentaCorrientePorCliente(recibo.getCliente());
      } else if (recibo.getProveedor() != null) {
        cc = this.getCuentaCorrientePorProveedor(recibo.getProveedor());
      }
      if (null == cc) {
        throw new BusinessServiceException(messageSource.getMessage(MENSAJE_CC_NO_EXISTENTE, null, Locale.getDefault()));
      }
      this.setSaldoCuentaCorriente(cc, cc.getSaldo().subtract(recibo.getMonto()));
      rcc = this.getRenglonCuentaCorrienteDeRecibo(recibo, false);
      this.cambiarFechaUltimoComprobante(cc, rcc);
      rcc.setEliminado(true);
      log.info(messageSource.getMessage(MENSAJE_RENGLON_CC_ELIMINADO, null, Locale.getDefault()), rcc);
    }
  }

  private void setSaldoCuentaCorriente(CuentaCorriente cuentaCorriente, BigDecimal saldo) {
    if (saldo.setScale(2, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) == 0) {
      cuentaCorriente.setSaldo(BigDecimal.ZERO);
    } else {
      cuentaCorriente.setSaldo(saldo);
    }
  }

  private void cambiarFechaUltimoComprobante(CuentaCorriente cc, RenglonCuentaCorriente rcc) {
    List<RenglonCuentaCorriente> ultimosDosMovimientos = this.getUltimosDosMovimientos(cc);
    if (ultimosDosMovimientos.size() == 2
        && ultimosDosMovimientos
            .get(0)
            .getIdRenglonCuentaCorriente()
            .equals(rcc.getIdRenglonCuentaCorriente())) {
      cc.setFechaUltimoMovimiento(ultimosDosMovimientos.get(1).getFecha());
    } else if (ultimosDosMovimientos.size() == 1) {
      cc.setFechaUltimoMovimiento(null);
    }
  }

  @Override
  public byte[] getReporteCuentaCorrienteCliente(CuentaCorrienteCliente cuentaCorrienteCliente, FormatoReporte formato) {
    var renglonesCuentaCorriente = this.getRenglonesCuentaCorrienteParaReporte(cuentaCorrienteCliente.getIdCuentaCorriente());
    Map<String, Object> params = new HashMap<>();
    params.put("cuentaCorrienteCliente", cuentaCorrienteCliente);
    var sucursalPredeterminada = sucursalService.getSucursalPredeterminada();
    if (sucursalPredeterminada.getLogo() != null && !sucursalPredeterminada.getLogo().isEmpty()) {
      try {
        params.put("logo", new ImageIcon(ImageIO.read(new URL(sucursalPredeterminada.getLogo()))).getImage());
      } catch (IOException ex) {
        throw new ServiceException(messageSource.getMessage("mensaje_sucursal_404_logo", null, Locale.getDefault()), ex);
      }
    }
    return jasperReportsHandler.compilar("report/CuentaCorriente.jrxml", params, renglonesCuentaCorriente, formato);
  }

  @Override
  public byte[] getReporteListaDeCuentasCorrienteClientePorCriteria(BusquedaCuentaCorrienteClienteCriteria criteria,
                                                                    long idUsuarioLoggedIn, FormatoReporte formato) {
    var cuentaCorrienteClientes = this.buscarCuentasCorrienteClienteParaReporte(criteria, idUsuarioLoggedIn);
    Map<String, Object> params = new HashMap<>();
    var sucursalPredeterminada =  sucursalService.getSucursalPredeterminada();
    if (sucursalPredeterminada.getLogo() != null && !sucursalPredeterminada.getLogo().isEmpty()) {
      try {
        params.put("logo", new ImageIcon(ImageIO.read(new URL(sucursalPredeterminada.getLogo()))).getImage());
      } catch (IOException ex) {
        throw new ServiceException(messageSource.getMessage("mensaje_sucursal_404_logo", null, Locale.getDefault()), ex);
      }
    }
    return jasperReportsHandler.compilar("report/ListaClientes.jrxml", params, cuentaCorrienteClientes, formato);
  }

  @Override
  public RenglonCuentaCorriente guardar(RenglonCuentaCorriente renglonCuentaCorriente) {
    return renglonCuentaCorrienteRepository.save(renglonCuentaCorriente);
  }

  @Override
  public RenglonCuentaCorriente getRenglonCuentaCorrienteDeFactura(Factura factura, boolean eliminado) {
    return renglonCuentaCorrienteRepository.findByFacturaAndEliminado(factura, eliminado);
  }

  @Override
  public RenglonCuentaCorriente getRenglonCuentaCorrienteDeNota(Nota nota, boolean eliminado) {
    return renglonCuentaCorrienteRepository.findByNotaAndEliminado(nota, eliminado);
  }

  @Override
  public RenglonCuentaCorriente getRenglonCuentaCorrienteDeRecibo(Recibo recibo, boolean eliminado) {
    return renglonCuentaCorrienteRepository.findByReciboAndEliminado(recibo, eliminado);
  }

  @Override
  public RenglonCuentaCorriente getRenglonCuentaCorrienteDeRemito(Remito remito, boolean eliminado) {
    return renglonCuentaCorrienteRepository.findByRemitoAndEliminado(remito, eliminado);
  }

  @Override
  public Page<RenglonCuentaCorriente> getRenglonesCuentaCorriente(long idCuentaCorriente, Integer pagina) {
    Pageable pageable = PageRequest.of(pagina, TAMANIO_PAGINA_DEFAULT);
    return renglonCuentaCorrienteRepository.findAllByCuentaCorrienteAndEliminado(
        idCuentaCorriente, pageable);
  }

  @Override
  public List<RenglonCuentaCorriente> getRenglonesCuentaCorrienteParaReporte(long idCuentaCorriente) {
    return renglonCuentaCorrienteRepository.findAllByCuentaCorrienteAndEliminado(idCuentaCorriente);
  }

  @Override
  public List<RenglonCuentaCorriente> getUltimosDosMovimientos(CuentaCorriente cuentaCorriente) {
    return renglonCuentaCorrienteRepository
        .findTop2ByAndCuentaCorrienteAndEliminadoOrderByIdRenglonCuentaCorrienteDesc(
            cuentaCorriente, false);
  }

}
