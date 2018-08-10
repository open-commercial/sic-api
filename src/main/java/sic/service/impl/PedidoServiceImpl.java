package sic.service.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.*;
import javax.imageio.ImageIO;
import javax.persistence.EntityNotFoundException;
import javax.swing.ImageIcon;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sic.modelo.*;
import sic.service.*;
import sic.repository.PedidoRepository;
import sic.util.FormatterFechaHora;

@Service
public class PedidoServiceImpl implements IPedidoService {

    private final PedidoRepository pedidoRepository;
    private final IFacturaService facturaService;
    private final IUsuarioService usuarioService;
    private final IClienteService clienteService;
    private final IProductoService productoService;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    public PedidoServiceImpl(IFacturaService facturaService, PedidoRepository pedidoRepository,
                             IUsuarioService usuarioService, IClienteService clienteService,
                             IProductoService productoService) {
        this.facturaService = facturaService;
        this.pedidoRepository = pedidoRepository;
        this.usuarioService = usuarioService;
        this.clienteService = clienteService;
        this.productoService = productoService;
    }

    @Override
    public Pedido getPedidoPorId(Long idPedido) {
        Pedido pedido = pedidoRepository.findById(idPedido);
        if (pedido == null) {
            throw new EntityNotFoundException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_pedido_no_existente"));
        }
        return pedido;
    }

    private void validarPedido(TipoDeOperacion operacion, Pedido pedido) {
        //Entrada de Datos
        //Requeridos
        if (pedido.getFecha() == null) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_pedido_fecha_vacia"));
        }        
        if (pedido.getRenglones() == null || pedido.getRenglones().isEmpty()) {  
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_pedido_renglones_vacio"));
        }
        for (RenglonPedido r : pedido.getRenglones()) {
          if (r.getProducto() == null) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
              .getString("mensaje_pedido_renglon_sin_producto"));
          }
        }
        if (pedido.getEmpresa() == null) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_pedido_empresa_vacia"));
        }
        if (pedido.getUsuario() == null) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_pedido_usuario_vacio"));
        }
        if (pedido.getCliente() == null) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_pedido_cliente_vacio"));
        }
        //Validar Estado
        EstadoPedido estado = pedido.getEstado();
        if ((estado != EstadoPedido.ABIERTO) && (estado != EstadoPedido.ACTIVO) && (estado != EstadoPedido.CERRADO)) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaja_estado_no_valido"));
        }
        if (operacion == TipoDeOperacion.ALTA) {
            //Duplicados       
            if (pedidoRepository.findByNroPedidoAndEmpresaAndEliminado(pedido.getNroPedido(), pedido.getEmpresa(), false) != null) {
                throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                        .getString("mensaje_pedido_duplicado"));
            }
        }
        if (operacion == TipoDeOperacion.ACTUALIZACION) {
            //Duplicados       
            if (pedidoRepository.findByNroPedidoAndEmpresaAndEliminado(pedido.getNroPedido(), pedido.getEmpresa(), false) == null) {
                throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                        .getString("mensaje_pedido_no_existente"));
            }
        }        
    }

    @Override
    public Pedido actualizarEstadoPedido(Pedido pedido) {
        pedido.setEstado(EstadoPedido.ACTIVO);
        if (this.getFacturasDelPedido(pedido.getId_Pedido()).isEmpty()) {
            pedido.setEstado(EstadoPedido.ABIERTO);
        }
        if (facturaService.pedidoTotalmenteFacturado(pedido)) {
            pedido.setEstado(EstadoPedido.CERRADO);
        }
        return pedido;
    }

    @Override
    public Pedido calcularTotalActualDePedido(Pedido pedido) {
        BigDecimal porcentajeDescuento;
        BigDecimal totalActual = BigDecimal.ZERO;
        for (RenglonPedido renglonPedido : this.getRenglonesDelPedido(pedido.getId_Pedido())) {
            porcentajeDescuento = BigDecimal.ONE.subtract(renglonPedido.getDescuento_porcentaje()
                    .divide(CIEN, 15, RoundingMode.HALF_UP));
            renglonPedido.setSubTotal(renglonPedido.getProducto().getPrecioLista()
                    .multiply(renglonPedido.getCantidad())
                    .multiply(porcentajeDescuento));
            totalActual = totalActual.add(renglonPedido.getSubTotal());
        }
        pedido.setTotalActual(totalActual);
        return pedido;
    }

  @Override
  public long calcularNumeroPedido(Empresa empresa) {
    long min = 1L;
    long max = 9999999999L; // 10 digitos
    long randomLong = 0L;
    boolean esRepetido = true;
    while (esRepetido) {
      randomLong = min + (long) (Math.random() * (max - min));
      Pedido p = pedidoRepository.findByNroPedidoAndEmpresaAndEliminado(randomLong, empresa, false);
      if (p == null) esRepetido = false;
    }
    return randomLong;
  }

    @Override
    public List<Factura> getFacturasDelPedido(long idPedido) {
        return facturaService.getFacturasDelPedido(idPedido);
    }

    @Override
    @Transactional
    public Pedido guardar(Pedido pedido) {
        pedido.setFecha(new Date());
        pedido.setNroPedido(this.calcularNumeroPedido(pedido.getEmpresa()));
        pedido.setEstado(EstadoPedido.ABIERTO);
        this.validarPedido(TipoDeOperacion.ALTA , pedido);
        pedido = pedidoRepository.save(pedido);
        logger.warn("El Pedido " + pedido + " se guardó correctamente.");
        return pedido;
    }

    @Override
    public Page<Pedido> buscarConCriteria(BusquedaPedidoCriteria criteria, long idUsuarioLoggedIn) {
        //Fecha
        if (criteria.isBuscaPorFecha() && (criteria.getFechaDesde() == null || criteria.getFechaHasta() == null)) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_pedido_fechas_busqueda_invalidas"));
        }
        if (criteria.isBuscaPorFecha()) {
            Calendar cal = new GregorianCalendar();
            cal.setTime(criteria.getFechaDesde());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            criteria.setFechaDesde(cal.getTime());
            cal.setTime(criteria.getFechaHasta());
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            criteria.setFechaHasta(cal.getTime());
        }
        QPedido qpedido = QPedido.pedido;
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(qpedido.empresa.id_Empresa.eq(criteria.getIdEmpresa()).and(qpedido.eliminado.eq(false)));
        if (criteria.isBuscaPorFecha()) {
            FormatterFechaHora formateadorFecha = new FormatterFechaHora(FormatterFechaHora.FORMATO_FECHAHORA_INTERNACIONAL);
            DateExpression<Date> fDesde = Expressions.dateTemplate(Date.class, "convert({0}, datetime)", formateadorFecha.format(criteria.getFechaDesde()));
            DateExpression<Date> fHasta = Expressions.dateTemplate(Date.class, "convert({0}, datetime)", formateadorFecha.format(criteria.getFechaHasta()));            
            builder.and(qpedido.fecha.between(fDesde, fHasta));
        }
        if (criteria.isBuscaCliente()) builder.and(qpedido.cliente.id_Cliente.eq(criteria.getIdCliente()));
        if (criteria.isBuscaUsuario()) builder.and(qpedido.usuario.id_Usuario.eq(criteria.getIdUsuario()));
        if (criteria.isBuscaPorNroPedido()) builder.and(qpedido.nroPedido.eq(criteria.getNroPedido()));
        if (criteria.isBuscaPorEstadoPedido()) builder.and(qpedido.estado.eq(criteria.getEstadoPedido()));
        Usuario usuarioLogueado = usuarioService.getUsuarioPorId(idUsuarioLoggedIn);
        BooleanBuilder rsPredicate = new BooleanBuilder();
        if (!usuarioLogueado.getRoles().contains(Rol.ADMINISTRADOR)
                && !usuarioLogueado.getRoles().contains(Rol.VENDEDOR)
                && !usuarioLogueado.getRoles().contains(Rol.ENCARGADO)) {
            for (Rol rol : usuarioLogueado.getRoles()) {
                switch (rol) {
                    case VIAJANTE:
                        rsPredicate.or(qpedido.cliente.viajante.eq(usuarioLogueado));
                        break;
                    case COMPRADOR:
                        Cliente clienteRelacionado =
                                clienteService.getClientePorIdUsuarioYidEmpresa(
                                        idUsuarioLoggedIn, criteria.getIdEmpresa());
                        if (clienteRelacionado != null) {
                            rsPredicate.or(qpedido.cliente.eq(clienteRelacionado));
                        }
                        break;
                }
            }
            builder.and(rsPredicate);
        }
        Page<Pedido> pedidos = pedidoRepository.findAll(builder, criteria.getPageable());
        pedidos.getContent().forEach(this::calcularTotalActualDePedido);
        return pedidos;
    }

    @Override
    @Transactional
    public void actualizar(Pedido pedido) {
        this.validarPedido(TipoDeOperacion.ACTUALIZACION , pedido);
        pedidoRepository.save(pedido);
    }

    @Override
    @Transactional
    public boolean eliminar(long idPedido) {
        Pedido pedido = this.getPedidoPorId(idPedido);
        if (pedido.getEstado() == EstadoPedido.ABIERTO) {
            pedido.setEliminado(true);
            pedidoRepository.save(pedido);
        }
        return pedido.isEliminado();
    }

    @Override
    public List<RenglonPedido> getRenglonesDelPedido(Long idPedido) {
        return this.getPedidoPorId(idPedido).getRenglones();
    }

    @Override
    public HashMap<Long, RenglonFactura> getRenglonesFacturadosDelPedido(long nroPedido) {
        List<RenglonFactura> renglonesDeFacturas = new ArrayList<>();
        this.getFacturasDelPedido(nroPedido).forEach(f ->
            f.getRenglones().forEach(r -> renglonesDeFacturas.add(facturaService.calcularRenglon(f.getTipoComprobante(),
                        Movimiento.VENTA, r.getCantidad(), r.getId_ProductoItem(), r.getDescuento_porcentaje(),false)))
        );
        HashMap<Long, RenglonFactura> listaRenglonesUnificados = new HashMap<>();
        if (!renglonesDeFacturas.isEmpty()) {
            renglonesDeFacturas.forEach(r -> {
                if (listaRenglonesUnificados.containsKey(r.getId_ProductoItem())) {
                    listaRenglonesUnificados.get(r.getId_ProductoItem())
                            .setCantidad(listaRenglonesUnificados
                                    .get(r.getId_ProductoItem()).getCantidad().add(r.getCantidad()));
                } else {
                    listaRenglonesUnificados.put(r.getId_ProductoItem(), r);
                }
            });
        }
        return listaRenglonesUnificados;
    }

    @Override
    public byte[] getReportePedido(Pedido pedido) {
        ClassLoader classLoader = PedidoServiceImpl.class.getClassLoader();
        InputStream isFileReport = classLoader.getResourceAsStream("sic/vista/reportes/Pedido.jasper");
        Map<String, Object> params = new HashMap<>();
        params.put("pedido", pedido);
        if (!pedido.getEmpresa().getLogo().isEmpty()) {
            try {
                params.put("logo", new ImageIcon(ImageIO.read(new URL(pedido.getEmpresa().getLogo()))).getImage());
            } catch (IOException ex) {
                logger.error(ex.getMessage());
                throw new ServiceException(ResourceBundle.getBundle("Mensajes")
                        .getString("mensaje_empresa_404_logo"), ex);
            }
        }
        List<RenglonPedido> renglones = this.getRenglonesDelPedido(pedido.getId_Pedido());
        JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(renglones);
        try {
            return JasperExportManager.exportReportToPdf(JasperFillManager.fillReport(isFileReport, params, ds));
        } catch (JRException ex) {
            logger.error(ex.getMessage());
            throw new ServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_error_reporte"), ex);
        }
    }

    @Override
    public BigDecimal calcularDescuentoNeto(BigDecimal precioUnitario, BigDecimal descuentoPorcentaje) {
        BigDecimal resultado = BigDecimal.ZERO;
        if (descuentoPorcentaje != BigDecimal.ZERO) {
            resultado = precioUnitario.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
        }
        return resultado;
    }

    @Override
    public BigDecimal calcularSubTotal(BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuentoNeto) {
        return (precioUnitario.subtract(descuentoNeto)).multiply(cantidad);
    }

  @Override
  public RenglonPedido calcularRenglonPedido(
      long idProducto, BigDecimal cantidad, BigDecimal descuentoPorcentaje) {
    RenglonPedido nuevoRenglon = new RenglonPedido();
    Producto productoDelRenglon = productoService.getProductoPorId(idProducto);
    nuevoRenglon.setProducto(productoDelRenglon);
    nuevoRenglon.setCantidad(cantidad);
    nuevoRenglon.setDescuento_porcentaje(descuentoPorcentaje);
    nuevoRenglon.setDescuento_neto(
        this.calcularDescuentoNeto(
            nuevoRenglon.getProducto().getPrecioLista(), descuentoPorcentaje));
    nuevoRenglon.setSubTotal(
        this.calcularSubTotal(
            nuevoRenglon.getCantidad(),
            nuevoRenglon.getProducto().getPrecioLista(),
            nuevoRenglon.getDescuento_neto()));
    return nuevoRenglon;
  }

  @Override
  public List<RenglonPedido> convertirRenglonesFacturaEnRenglonesPedido(
      List<RenglonFactura> renglonesFactura) {
    List<RenglonPedido> renglonesPedido = new ArrayList<>();
    renglonesFactura.forEach(rengonFactura -> renglonesPedido.add(
      this.calcularRenglonPedido(
        rengonFactura.getId_ProductoItem(),
        rengonFactura.getCantidad(),
        rengonFactura.getDescuento_porcentaje())));
    return renglonesPedido;
  }

}
