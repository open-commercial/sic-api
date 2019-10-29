package sic.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.springframework.context.MessageSource;
import org.springframework.validation.annotation.Validated;
import sic.modelo.*;
import sic.modelo.criteria.BusquedaCajaCriteria;
import sic.service.*;

import javax.persistence.EntityNotFoundException;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sic.exception.BusinessServiceException;
import sic.repository.CajaRepository;

@Service
@Validated
public class CajaServiceImpl implements ICajaService {

  private final CajaRepository cajaRepository;
  private final IFormaDePagoService formaDePagoService;
  private final IGastoService gastoService;
  private final IEmpresaService empresaService;
  private final IUsuarioService usuarioService;
  private final IReciboService reciboService;
  private final IClockService clockService;
  private static final int TAMANIO_PAGINA_DEFAULT = 25;
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final MessageSource messageSource;

  @Autowired
  public CajaServiceImpl(
      CajaRepository cajaRepository,
      IFormaDePagoService formaDePagoService,
      IGastoService gastoService,
      IEmpresaService empresaService,
      IUsuarioService usuarioService,
      IReciboService reciboService,
      IClockService clockService,
      MessageSource messageSource) {
    this.cajaRepository = cajaRepository;
    this.formaDePagoService = formaDePagoService;
    this.gastoService = gastoService;
    this.empresaService = empresaService;
    this.usuarioService = usuarioService;
    this.reciboService = reciboService;
    this.clockService = clockService;
    this.messageSource = messageSource;
  }

  @Override
  public void validarOperacion(@Valid Caja caja) {
    // Una Caja por dia
    Caja ultimaCaja = this.getUltimaCaja(caja.getEmpresa().getId_Empresa());
    if (ultimaCaja != null) {
      if (ultimaCaja.getEstado() == EstadoCaja.ABIERTA) {
        throw new BusinessServiceException(
            messageSource.getMessage("mensaje_caja_anterior_abierta", null, Locale.getDefault()));
      }
      if (ultimaCaja.getFechaApertura().isBefore(caja.getFechaApertura())) {
        throw new BusinessServiceException(
            messageSource.getMessage(
                "mensaje_fecha_apertura_no_valida", null, Locale.getDefault()));
      }
    }
    // Duplicados
    if (cajaRepository.findById(caja.getId_Caja()) != null) {
      throw new BusinessServiceException(
          messageSource.getMessage("mensaje_caja_duplicada", null, Locale.getDefault()));
    }
  }

  @Override
  public void validarMovimiento(LocalDateTime fechaMovimiento, long idEmpresa) {
    Caja caja = this.getUltimaCaja(idEmpresa);
    if (caja == null) {
      throw new BusinessServiceException(
          messageSource.getMessage("mensaje_caja_no_existente", null, Locale.getDefault()));
    }
    if (caja.getEstado().equals(EstadoCaja.CERRADA)) {
      throw new BusinessServiceException(
          messageSource.getMessage("mensaje_caja_cerrada", null, Locale.getDefault()));
    }
    if (fechaMovimiento.isBefore(caja.getFechaApertura())) {
      throw new BusinessServiceException(
          messageSource.getMessage(
              "mensaje_caja_movimiento_fecha_no_valida", null, Locale.getDefault()));
    }
  }

  @Override
  @Transactional
  public Caja abrirCaja(Empresa empresa, Usuario usuarioApertura, BigDecimal saldoApertura) {
    Caja caja = new Caja();
    caja.setEstado(EstadoCaja.ABIERTA);
    caja.setEmpresa(empresa);
    caja.setSaldoApertura(saldoApertura);
    caja.setUsuarioAbreCaja(usuarioApertura);
    caja.setFechaApertura(this.clockService.getFechaActual());
    this.validarOperacion(caja);
    return cajaRepository.save(caja);
  }

  @Override
  public void actualizar(@Valid Caja caja) {
    cajaRepository.save(caja);
  }

  @Override
  @Transactional
  public void eliminar(Long idCaja) {
    Caja caja = this.getCajaPorId(idCaja);
    if (caja == null) {
      throw new EntityNotFoundException(messageSource.getMessage(
        "mensaje_caja_no_existente", null, Locale.getDefault()));
    }
    caja.setEliminada(true);
    this.actualizar(caja);
  }

  @Override
  public Caja getUltimaCaja(long idEmpresa) {
    Pageable pageable = PageRequest.of(0, 1);
    List<Caja> topCaja =
        cajaRepository
            .findTopByEmpresaAndEliminadaOrderByIdCajaDesc(idEmpresa, pageable)
            .getContent();
    return (topCaja.isEmpty()) ? null : topCaja.get(0);
  }

  @Override
  public Caja getCajaPorId(Long idCaja) {
    Optional<Caja> caja = cajaRepository.findById(idCaja);
    if (caja.isPresent() && !caja.get().isEliminada()) {
      return caja.get();
    } else {
      throw new EntityNotFoundException(messageSource.getMessage(
        "mensaje_caja_no_existente", null, Locale.getDefault()));
    }
  }

  @Override
  public Page<Caja> buscarCajas(BusquedaCajaCriteria criteria) {
    return cajaRepository.findAll(
        this.getBuilder(criteria),
        this.getPageable(criteria.getPagina(), criteria.getOrdenarPor(), criteria.getSentido()));
  }

  private Pageable getPageable(int pagina, String ordenarPor, String sentido) {
    String ordenDefault = "fechaApertura";
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

  private BooleanBuilder getBuilder(BusquedaCajaCriteria criteria) {
    QCaja qCaja = QCaja.caja;
    BooleanBuilder builder = new BooleanBuilder();
    builder.and(
        qCaja.empresa.id_Empresa.eq(criteria.getIdEmpresa()).and(qCaja.eliminada.eq(false)));
    if (criteria.getIdUsuarioApertura() != null && criteria.getIdUsuarioCierre() == null) {
      builder.and(qCaja.usuarioAbreCaja.id_Usuario.eq(criteria.getIdUsuarioApertura()));
    }
    if (criteria.getIdUsuarioApertura() == null && criteria.getIdUsuarioCierre() != null) {
      builder.and(qCaja.usuarioCierraCaja.id_Usuario.eq(criteria.getIdUsuarioCierre()));
    }
    if (criteria.getIdUsuarioApertura() != null && criteria.getIdUsuarioCierre() != null) {
      builder.and(
          qCaja
              .usuarioAbreCaja
              .id_Usuario
              .eq(criteria.getIdUsuarioApertura())
              .and(qCaja.usuarioCierraCaja.id_Usuario.eq(criteria.getIdUsuarioCierre())));
    }
    if (criteria.getFechaDesde() != null || criteria.getFechaHasta() != null) {
      criteria.setFechaDesde(criteria.getFechaDesde().withHour(0).withMinute(0).withSecond(0));
      criteria.setFechaHasta(criteria.getFechaHasta().withHour(23).withMinute(59).withSecond(59));
      String dateTemplate = "convert({0}, datetime)";
      if (criteria.getFechaDesde() != null && criteria.getFechaHasta() != null) {
        DateExpression<LocalDateTime> fDesde =
            Expressions.dateTemplate(
                LocalDateTime.class,
                dateTemplate,
                criteria.getFechaDesde().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        DateExpression<LocalDateTime> fHasta =
            Expressions.dateTemplate(
                LocalDateTime.class,
                dateTemplate,
                criteria.getFechaHasta().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        builder.and(qCaja.fechaApertura.between(fDesde, fHasta));
      } else if (criteria.getFechaDesde() != null) {
        DateExpression<LocalDateTime> fDesde =
            Expressions.dateTemplate(
                LocalDateTime.class,
                dateTemplate,
                criteria.getFechaDesde().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        builder.and(qCaja.fechaApertura.after(fDesde));
      } else if (criteria.getFechaHasta() != null) {
        DateExpression<LocalDateTime> fHasta =
            Expressions.dateTemplate(
                LocalDateTime.class,
                dateTemplate,
                criteria.getFechaHasta().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        builder.and(qCaja.fechaApertura.before(fHasta));
      }
    }
    return builder;
  }

  @Override
  public Caja cerrarCaja(long idCaja, BigDecimal monto, Long idUsuario, boolean scheduling) {
    Caja cajaACerrar = this.getCajaPorId(idCaja);
    cajaACerrar.setSaldoReal(monto);
    if (scheduling) {
      cajaACerrar.setFechaCierre(
          cajaACerrar.getFechaApertura().withHour(23).withMinute(59).withSecond(59));
    } else {
      cajaACerrar.setFechaCierre(this.clockService.getFechaActual());
    }
    if (idUsuario != null) {
      cajaACerrar.setUsuarioCierraCaja(usuarioService.getUsuarioNoEliminadoPorId(idUsuario));
    }
    cajaACerrar.setSaldoSistema(this.getSaldoSistema(cajaACerrar));
    cajaACerrar.setEstado(EstadoCaja.CERRADA);
    this.actualizar(cajaACerrar);
    logger.warn("La Caja {} se cerró correctamente.", cajaACerrar);
    return cajaACerrar;
  }

  @Scheduled(cron = "30 0 0 * * *") // Todos los dias a las 00:00:30
  public void cerrarCajas() {
    logger.warn("Cierre automático de Cajas a las {}", LocalDateTime.now());
    List<Empresa> empresas = this.empresaService.getEmpresas();
    empresas.stream()
        .map(empresa -> this.getUltimaCaja(empresa.getId_Empresa()))
        .filter(
            ultimaCajaDeEmpresa ->
                ((ultimaCajaDeEmpresa != null)
                    && (ultimaCajaDeEmpresa.getEstado() == EstadoCaja.ABIERTA)))
        .forEachOrdered(
            ultimaCajaDeEmpresa -> {
              if (ultimaCajaDeEmpresa.getFechaApertura().isBefore(LocalDateTime.now())) {
                this.cerrarCaja(
                    ultimaCajaDeEmpresa.getId_Caja(),
                    this.getSaldoQueAfectaCaja(ultimaCajaDeEmpresa),
                    ultimaCajaDeEmpresa.getUsuarioAbreCaja().getId_Usuario(),
                    true);
              }
            });
  }

  @Override
  public BigDecimal getSaldoQueAfectaCaja(Caja caja) {
    LocalDateTime fechaHasta = LocalDateTime.now();
    if (caja.getFechaCierre() != null) {
      fechaHasta = caja.getFechaCierre();
    }
    BigDecimal totalRecibosCliente =
        reciboService.getTotalRecibosClientesQueAfectanCajaEntreFechas(
            caja.getEmpresa().getId_Empresa(), caja.getFechaApertura(), fechaHasta);
    BigDecimal totalRecibosProveedor =
        reciboService.getTotalRecibosProveedoresQueAfectanCajaEntreFechas(
            caja.getEmpresa().getId_Empresa(), caja.getFechaApertura(), fechaHasta);
    BigDecimal totalGastos =
        gastoService.getTotalGastosQueAfectanCajaEntreFechas(
            caja.getEmpresa().getId_Empresa(), caja.getFechaApertura(), fechaHasta);
    return caja.getSaldoApertura()
        .add(totalRecibosCliente)
        .subtract(totalRecibosProveedor)
        .subtract(totalGastos);
  }

  @Override
  public BigDecimal getSaldoSistema(Caja caja) {
    if (caja.getEstado().equals(EstadoCaja.ABIERTA)) {
      LocalDateTime fechaHasta = LocalDateTime.now();
      if (caja.getFechaCierre() != null) {
        fechaHasta = caja.getFechaCierre();
      }
      BigDecimal totalRecibosCliente =
          reciboService.getTotalRecibosClientesEntreFechas(
              caja.getEmpresa().getId_Empresa(), caja.getFechaApertura(), fechaHasta);
      BigDecimal totalRecibosProveedor =
          reciboService.getTotalRecibosProveedoresEntreFechas(
              caja.getEmpresa().getId_Empresa(), caja.getFechaApertura(), fechaHasta);
      BigDecimal totalGastos =
          gastoService.getTotalGastosEntreFechas(
              caja.getEmpresa().getId_Empresa(), caja.getFechaApertura(), fechaHasta);
      return caja.getSaldoApertura()
          .add(totalRecibosCliente)
          .subtract(totalRecibosProveedor)
          .subtract(totalGastos);
    } else {
      return caja.getSaldoSistema();
    }
  }

  @Override
  public boolean isUltimaCajaAbierta(long idEmpresa) {
    Caja caja = cajaRepository.isUltimaCajaAbierta(idEmpresa);
    return (caja != null)
        && cajaRepository.isUltimaCajaAbierta(idEmpresa).getEstado().equals(EstadoCaja.ABIERTA);
  }

  private BigDecimal getTotalMovimientosPorFormaDePago(Caja caja, FormaDePago fdp) {
    LocalDateTime fechaHasta = LocalDateTime.now();
    if (caja.getFechaCierre() != null) {
      fechaHasta = caja.getFechaCierre();
    }
    BigDecimal recibosTotal =
        reciboService
            .getTotalRecibosClientesEntreFechasPorFormaDePago(
                caja.getEmpresa().getId_Empresa(),
                fdp.getId_FormaDePago(),
                caja.getFechaApertura(),
                fechaHasta)
            .subtract(
                reciboService.getTotalRecibosProveedoresEntreFechasPorFormaDePago(
                    caja.getEmpresa().getId_Empresa(),
                    fdp.getId_FormaDePago(),
                    caja.getFechaApertura(),
                    fechaHasta));
    BigDecimal gastosTotal =
        gastoService.getTotalGastosEntreFechasYFormaDePago(
            caja.getEmpresa().getId_Empresa(),
            fdp.getId_FormaDePago(),
            caja.getFechaApertura(),
            fechaHasta);
    return recibosTotal.subtract(gastosTotal);
  }

  @Override
  public Map<Long, BigDecimal> getTotalesDeFormaDePago(long idCaja) {
    Caja caja = cajaRepository.findById(idCaja);
    Map<Long, BigDecimal> totalesPorFomaDePago = new HashMap<>();
    formaDePagoService
        .getFormasDePago()
        .forEach(
            fdp -> {
              BigDecimal total = this.getTotalMovimientosPorFormaDePago(caja, fdp);
              if (total.compareTo(BigDecimal.ZERO) != 0) {
                totalesPorFomaDePago.put(fdp.getId_FormaDePago(), total);
              }
            });
    return totalesPorFomaDePago;
  }

  @Override
  public BigDecimal getSaldoSistemaCajas(BusquedaCajaCriteria criteria) {
    return cajaRepository.getSaldoSistemaCajas(this.getBuilder(criteria));
  }

  @Override
  public BigDecimal getSaldoRealCajas(BusquedaCajaCriteria criteria) {
    return cajaRepository.getSaldoRealCajas(this.getBuilder(criteria));
  }

  @Override
  public List<MovimientoCaja> getMovimientosPorFormaDePagoEntreFechas(
      Empresa empresa, FormaDePago formaDePago, LocalDateTime desde, LocalDateTime hasta) {
    List<MovimientoCaja> movimientos = new ArrayList<>();
    gastoService
        .getGastosEntreFechasYFormaDePago(empresa, formaDePago, desde, hasta)
        .forEach(gasto -> movimientos.add(new MovimientoCaja(gasto)));
    reciboService
        .getRecibosEntreFechasPorFormaDePago(desde, hasta, formaDePago, empresa)
        .forEach(recibo -> movimientos.add(new MovimientoCaja(recibo)));
    Collections.sort(movimientos);
    return movimientos;
  }

  @Override
  @Transactional
  public void reabrirCaja(long idCaja, BigDecimal saldoAperturaNuevo) {
    Caja caja = getCajaPorId(idCaja);
    Caja ultimaCaja = this.getUltimaCaja(caja.getEmpresa().getId_Empresa());
    if (ultimaCaja == null) {
      throw new BusinessServiceException(messageSource.getMessage(
        "mensaje_caja_no_existente", null, Locale.getDefault()));
    }
    if (caja.getId_Caja() == ultimaCaja.getId_Caja()) {
      caja.setSaldoSistema(null);
      caja.setSaldoApertura(saldoAperturaNuevo);
      caja.setSaldoReal(null);
      caja.setEstado(EstadoCaja.ABIERTA);
      caja.setUsuarioCierraCaja(null);
      caja.setFechaCierre(null);
      this.actualizar(caja);
    } else {
      throw new EntityNotFoundException(messageSource.getMessage(
        "mensaje_caja_re_apertura_no_valida", null, Locale.getDefault()));
    }
  }

  @Override
  public Caja encontrarCajaCerradaQueContengaFechaEntreFechaAperturaYFechaCierre(
      long idEmpresa, LocalDateTime fecha) {
    return cajaRepository.encontrarCajaCerradaQueContengaFechaEntreFechaAperturaYFechaCierre(
        idEmpresa, fecha);
  }

  @Override
  @Transactional
  public int actualizarSaldoSistema(Caja caja, BigDecimal monto) {
    return cajaRepository.actualizarSaldoSistema(caja.getId_Caja(), monto);
  }
}
