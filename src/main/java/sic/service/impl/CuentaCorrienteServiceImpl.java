package sic.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import javax.validation.Valid;

import com.querydsl.core.BooleanBuilder;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
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
import org.springframework.validation.annotation.Validated;
import sic.modelo.*;
import sic.modelo.criteria.BusquedaCuentaCorrienteClienteCriteria;
import sic.modelo.criteria.BusquedaCuentaCorrienteProveedorCriteria;
import sic.repository.CuentaCorrienteClienteRepository;
import sic.repository.CuentaCorrienteProveedorRepository;
import sic.repository.CuentaCorrienteRepository;
import sic.repository.RenglonCuentaCorrienteRepository;
import sic.service.*;
import sic.exception.BusinessServiceException;
import sic.exception.ServiceException;

@Service
@Validated
public class CuentaCorrienteServiceImpl implements ICuentaCorrienteService {

  private final CuentaCorrienteRepository cuentaCorrienteRepository;
  private final CuentaCorrienteClienteRepository cuentaCorrienteClienteRepository;
  private final CuentaCorrienteProveedorRepository cuentaCorrienteProveedorRepository;
  private final RenglonCuentaCorrienteRepository renglonCuentaCorrienteRepository;
  private final IUsuarioService usuarioService;
  private final IClienteService clienteService;
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private static final int TAMANIO_PAGINA_DEFAULT = 25;
  private final MessageSource messageSource;

  @Autowired
  @Lazy
  public CuentaCorrienteServiceImpl(
      CuentaCorrienteRepository cuentaCorrienteRepository,
      CuentaCorrienteClienteRepository cuentaCorrienteClienteRepository,
      CuentaCorrienteProveedorRepository cuentaCorrienteProveedorRepository,
      RenglonCuentaCorrienteRepository renglonCuentaCorrienteRepository,
      IUsuarioService usuarioService, IClienteService clienteService,
      MessageSource messageSource) {
    this.cuentaCorrienteRepository = cuentaCorrienteRepository;
    this.cuentaCorrienteClienteRepository = cuentaCorrienteClienteRepository;
    this.cuentaCorrienteProveedorRepository = cuentaCorrienteProveedorRepository;
    this.renglonCuentaCorrienteRepository = renglonCuentaCorrienteRepository;
    this.usuarioService = usuarioService;
    this.clienteService = clienteService;
    this.messageSource = messageSource;
  }

  @Override
  @Transactional
  public CuentaCorrienteCliente guardarCuentaCorrienteCliente(
      @Valid CuentaCorrienteCliente cuentaCorrienteCliente) {
    this.validarOperacion(cuentaCorrienteCliente);
    cuentaCorrienteCliente = cuentaCorrienteClienteRepository.save(cuentaCorrienteCliente);
    logger.warn("La Cuenta Corriente Cliente {} se guardó correctamente.", cuentaCorrienteCliente);
    return cuentaCorrienteCliente;
  }

  @Override
  @Transactional
  public CuentaCorrienteProveedor guardarCuentaCorrienteProveedor(
      @Valid CuentaCorrienteProveedor cuentaCorrienteProveedor) {
    this.validarOperacion(cuentaCorrienteProveedor);
    cuentaCorrienteProveedor = cuentaCorrienteProveedorRepository.save(cuentaCorrienteProveedor);
    logger.warn(
        "La Cuenta Corriente Proveedor {} se guardó correctamente.", cuentaCorrienteProveedor);
    return cuentaCorrienteProveedor;
  }

  @Override
  public BigDecimal getSaldoCuentaCorriente(long idCliente) {
    return cuentaCorrienteClienteRepository.getSaldoCuentaCorrienteCliente(idCliente);
  }

  @Override
  public void validarOperacion(CuentaCorriente cuentaCorriente) {
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
  public Page<CuentaCorrienteCliente> buscarCuentaCorrienteCliente(
    BusquedaCuentaCorrienteClienteCriteria criteria, long idUsuarioLoggedIn) {
    QCuentaCorrienteCliente qCuentaCorrienteCliente =
        QCuentaCorrienteCliente.cuentaCorrienteCliente;
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
    if (criteria.getIdFiscal() != null)
      builder.or(qCuentaCorrienteCliente.cliente.idFiscal.eq(criteria.getIdFiscal()));
    if (criteria.getNroDeCliente() != null)
      builder.or(
          qCuentaCorrienteCliente.cliente.nroCliente.containsIgnoreCase(
              criteria.getNroDeCliente()));
    if (criteria.getIdViajante() != null)
      builder.and(qCuentaCorrienteCliente.cliente.viajante.idUsuario.eq(criteria.getIdViajante()));
    if (criteria.getIdLocalidad() != null)
      builder.and(
          qCuentaCorrienteCliente.cliente.ubicacionFacturacion.localidad.idLocalidad.eq(
              criteria.getIdLocalidad()));
    if (criteria.getIdProvincia() != null)
      builder.and(
          qCuentaCorrienteCliente.cliente.ubicacionFacturacion.localidad.provincia.idProvincia.eq(
              criteria.getIdProvincia()));
    Usuario usuarioLogueado = usuarioService.getUsuarioNoEliminadoPorId(idUsuarioLoggedIn);
    if (!usuarioLogueado.getRoles().contains(Rol.ADMINISTRADOR)
        && !usuarioLogueado.getRoles().contains(Rol.VENDEDOR)
        && !usuarioLogueado.getRoles().contains(Rol.ENCARGADO)) {
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (Rol rol : usuarioLogueado.getRoles()) {
        switch (rol) {
          case VIAJANTE:
            rsPredicate.or(qCuentaCorrienteCliente.cliente.viajante.eq(usuarioLogueado));
            break;
          case COMPRADOR:
            Cliente clienteRelacionado =
                clienteService.getClientePorIdUsuario(idUsuarioLoggedIn);
            if (clienteRelacionado != null) {
              rsPredicate.or(qCuentaCorrienteCliente.cliente.eq(clienteRelacionado));
            }
            break;
          default:
            rsPredicate.or(qCuentaCorrienteCliente.cliente.isNull());
            break;
        }
      }
      builder.and(rsPredicate);
    }
    builder.and(qCuentaCorrienteCliente.eliminada.eq(false));
    return cuentaCorrienteClienteRepository.findAll(
        builder,
        this.getPageable(criteria.getPagina(), criteria.getOrdenarPor(), criteria.getSentido(), "cliente.nombreFiscal"));
  }

  private Pageable getPageable(Integer pagina, String ordenarPor, String sentido, String ordenDefault) {
    if (pagina == null) pagina = 0;
    if (ordenarPor == null || sentido == null) {
      return PageRequest.of(
          pagina, TAMANIO_PAGINA_DEFAULT, new Sort(Sort.Direction.DESC, ordenDefault));
    } else {
      switch (sentido) {
        case "ASC":
          return PageRequest.of(
              pagina, TAMANIO_PAGINA_DEFAULT, new Sort(Sort.Direction.ASC, ordenarPor));
        case "DESC":
          return PageRequest.of(
              pagina, TAMANIO_PAGINA_DEFAULT, new Sort(Sort.Direction.DESC, ordenarPor));
        default:
          return PageRequest.of(
              pagina, TAMANIO_PAGINA_DEFAULT, new Sort(Sort.Direction.DESC, ordenDefault));
      }
    }
  }

  @Override
  public Page<CuentaCorrienteProveedor> buscarCuentaCorrienteProveedor(
      BusquedaCuentaCorrienteProveedorCriteria criteria) {
    QCuentaCorrienteProveedor qCuentaCorrienteProveedor =
        QCuentaCorrienteProveedor.cuentaCorrienteProveedor;
    BooleanBuilder builder = new BooleanBuilder();
    if (criteria.getNroProveedor() != null)
      builder.or(
          qCuentaCorrienteProveedor.proveedor.nroProveedor.containsIgnoreCase(
              criteria.getNroProveedor()));
    if (criteria.getRazonSocial() != null) {
      String[] terminos = criteria.getRazonSocial().split(" ");
      BooleanBuilder rsPredicate = new BooleanBuilder();
      for (String termino : terminos) {
        rsPredicate.and(
            qCuentaCorrienteProveedor.proveedor.razonSocial.containsIgnoreCase(termino));
      }
      builder.or(rsPredicate);
    }
    if (criteria.getIdFiscal() != null)
      builder.or(qCuentaCorrienteProveedor.proveedor.idFiscal.eq(criteria.getIdFiscal()));
    if (criteria.getIdLocalidad() != null)
      builder.and(
          qCuentaCorrienteProveedor.proveedor.ubicacion.localidad.idLocalidad.eq(
              criteria.getIdLocalidad()));
    if (criteria.getIdProvincia() != null)
      builder.and(
          qCuentaCorrienteProveedor.proveedor.ubicacion.localidad.provincia.idProvincia.eq(
              criteria.getIdProvincia()));
    builder.and(qCuentaCorrienteProveedor.eliminada.eq(false));
    return cuentaCorrienteProveedorRepository.findAll(
        builder,
        this.getPageable(
            criteria.getPagina(), criteria.getOrdenarPor(), criteria.getSentido(), "proveedor.razonSocial"));
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
    CuentaCorriente cc = this.getCuentaCorrientePorCliente(clienteService.getClienteNoEliminadoPorId(facturaVenta.getIdCliente()));
    if (null == cc) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_cuenta_corriente_no_existente", null, Locale.getDefault()));
    }
    cc.setFechaUltimoMovimiento(LocalDateTime.now());
    if (tipo == TipoDeOperacion.ALTA) {
      this.guardarRenglonCuentaCorrienteDeFactura(facturaVenta, cc);
    }
    if (tipo == TipoDeOperacion.ELIMINACION) {
      cc.setSaldo(cc.getSaldo().add(facturaVenta.getTotal()));
      RenglonCuentaCorriente rcc = this.getRenglonCuentaCorrienteDeFactura(facturaVenta, false);
      this.cambiarFechaUltimoComprobante(cc, rcc);
      rcc.setEliminado(true);
      logger.warn(messageSource.getMessage(
        "mensaje_reglon_cuenta_corriente_eliminado", null, Locale.getDefault()), rcc);
    }
  }

  @Override
  @Transactional
  public void asentarEnCuentaCorriente(FacturaCompra facturaCompra) {
    CuentaCorriente cc = this.getCuentaCorrientePorProveedor(facturaCompra.getProveedor());
    if (null == cc) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_cuenta_corriente_no_existente", null, Locale.getDefault()));
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
    cc.setSaldo(cc.getSaldo().add(rcc.getMonto()));
    cc.setFechaUltimoMovimiento(factura.getFecha());
    rcc.setCuentaCorriente(cc);
    this.renglonCuentaCorrienteRepository.save(rcc);
    logger.warn(messageSource.getMessage(
      "mensaje_reglon_cuenta_corriente_guardado", null, Locale.getDefault()), rcc);
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
      cc.setSaldo(cc.getSaldo().add(rcc.getMonto()));
      cc.setFechaUltimoMovimiento(nota.getFecha());
      rcc.setDescripcion(nota.getMotivo());
      rcc.setNota(nota);
      rcc.setFecha(nota.getFecha());
      rcc.setIdMovimiento(nota.getIdNota());
      cc.getRenglones().add(rcc);
      rcc.setCuentaCorriente(cc);
      this.renglonCuentaCorrienteRepository.save(rcc);
      logger.warn(messageSource.getMessage(
        "mensaje_reglon_cuenta_corriente_guardado", null, Locale.getDefault()), rcc);
    }
    if (tipo == TipoDeOperacion.ELIMINACION && nota.getCliente() != null) {
      RenglonCuentaCorriente rcc = this.getRenglonCuentaCorrienteDeNota(nota, false);
      cc.setSaldo(cc.getSaldo().subtract(rcc.getMonto()));
      this.cambiarFechaUltimoComprobante(cc, rcc);
      rcc.setEliminado(true);
      logger.warn(messageSource.getMessage(
        "mensaje_reglon_cuenta_corriente_eliminado", null, Locale.getDefault()), rcc);
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
        throw new BusinessServiceException(messageSource.getMessage(
          "mensaje_cuenta_corriente_no_existente", null, Locale.getDefault()));
      }
      cc.getRenglones().add(rcc);
      cc.setSaldo(cc.getSaldo().add(recibo.getMonto()));
      cc.setFechaUltimoMovimiento(recibo.getFecha());
      rcc.setCuentaCorriente(cc);
      this.renglonCuentaCorrienteRepository.save(rcc);
      logger.warn(messageSource.getMessage(
        "mensaje_reglon_cuenta_corriente_guardado", null, Locale.getDefault()), rcc);
    }
    if (tipo == TipoDeOperacion.ELIMINACION) {
      CuentaCorriente cc = null;
      if (recibo.getCliente() != null) {
        cc = this.getCuentaCorrientePorCliente(recibo.getCliente());
      } else if (recibo.getProveedor() != null) {
        cc = this.getCuentaCorrientePorProveedor(recibo.getProveedor());
      }
      if (null == cc) {
        throw new BusinessServiceException(messageSource.getMessage(
          "mensaje_cuenta_corriente_no_existente", null, Locale.getDefault()));
      }
      cc.setSaldo(cc.getSaldo().subtract(recibo.getMonto()));
      rcc = this.getRenglonCuentaCorrienteDeRecibo(recibo, false);
      this.cambiarFechaUltimoComprobante(cc, rcc);
      rcc.setEliminado(true);
      logger.warn(messageSource.getMessage(
        "mensaje_reglon_cuenta_corriente_eliminado", null, Locale.getDefault()), rcc);
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
  public byte[] getReporteCuentaCorrienteCliente(
      CuentaCorrienteCliente cuentaCorrienteCliente, String formato) {
    ClassLoader classLoader = CuentaCorrienteServiceImpl.class.getClassLoader();
    InputStream isFileReport =
        classLoader.getResourceAsStream("sic/vista/reportes/CuentaCorriente.jasper");
    JRBeanCollectionDataSource ds =
        new JRBeanCollectionDataSource(
            this.getRenglonesCuentaCorrienteParaReporte(
                cuentaCorrienteCliente.getIdCuentaCorriente()));
    Map<String, Object> params = new HashMap<>();
    params.put("cuentaCorrienteCliente", cuentaCorrienteCliente);
    switch (formato) {
      case "xlsx":
        try {
          return xlsReportToArray(JasperFillManager.fillReport(isFileReport, params, ds));
        } catch (JRException ex) {
          throw new ServiceException(
              messageSource.getMessage("mensaje_error_reporte", null, Locale.getDefault()), ex);
        }
      case "pdf":
        try {
          return JasperExportManager.exportReportToPdf(
              JasperFillManager.fillReport(isFileReport, params, ds));
        } catch (JRException ex) {
          throw new ServiceException(
              messageSource.getMessage("mensaje_error_reporte", null, Locale.getDefault()), ex);
        }
      default:
        throw new BusinessServiceException(
            messageSource.getMessage("mensaje_formato_no_valido", null, Locale.getDefault()));
    }
  }

  private byte[] xlsReportToArray(JasperPrint jasperPrint) {
    byte[] bytes = null;
    try {
      JRXlsxExporter jasperXlsxExportMgr = new JRXlsxExporter();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      SimpleOutputStreamExporterOutput simpleOutputStreamExporterOutput =
          new SimpleOutputStreamExporterOutput(out);
      jasperXlsxExportMgr.setExporterInput(new SimpleExporterInput(jasperPrint));
      jasperXlsxExportMgr.setExporterOutput(simpleOutputStreamExporterOutput);
      jasperXlsxExportMgr.exportReport();
      bytes = out.toByteArray();
      out.close();
    } catch (JRException ex) {
      logger.error(ex.getMessage());
      throw new ServiceException(messageSource.getMessage(
        "mensaje_error_reporte", null, Locale.getDefault()), ex);
    } catch (IOException ex) {
      logger.error(ex.getMessage());
    }
    return bytes;
  }

  @Override
  public RenglonCuentaCorriente guardar(RenglonCuentaCorriente renglonCuentaCorriente) {
    return renglonCuentaCorrienteRepository.save(renglonCuentaCorriente);
  }

  @Override
  public RenglonCuentaCorriente getRenglonCuentaCorrienteDeFactura(
      Factura factura, boolean eliminado) {
    return renglonCuentaCorrienteRepository.findByFacturaAndEliminado(factura, eliminado);
  }

  @Override
  public RenglonCuentaCorriente getRenglonCuentaCorrienteDeNota(Nota nota, boolean eliminado) {
    return renglonCuentaCorrienteRepository.findByNotaAndEliminado(nota, eliminado);
  }

  @Override
  public RenglonCuentaCorriente getRenglonCuentaCorrienteDeRecibo(
      Recibo recibo, boolean eliminado) {
    return renglonCuentaCorrienteRepository.findByReciboAndEliminado(recibo, eliminado);
  }

  @Override
  public Page<RenglonCuentaCorriente> getRenglonesCuentaCorriente(
      long idCuentaCorriente, Integer pagina) {
    Pageable pageable = PageRequest.of(pagina, TAMANIO_PAGINA_DEFAULT);
    return renglonCuentaCorrienteRepository.findAllByCuentaCorrienteAndEliminado(
        idCuentaCorriente, pageable);
  }

  @Override
  public List<RenglonCuentaCorriente> getRenglonesCuentaCorrienteParaReporte(
      long idCuentaCorriente) {
    return renglonCuentaCorrienteRepository.findAllByCuentaCorrienteAndEliminado(idCuentaCorriente);
  }

  @Override
  public List<RenglonCuentaCorriente> getUltimosDosMovimientos(CuentaCorriente cuentaCorriente) {
    return renglonCuentaCorrienteRepository
        .findTop2ByAndCuentaCorrienteAndEliminadoOrderByIdRenglonCuentaCorrienteDesc(
            cuentaCorriente, false);
  }
}
