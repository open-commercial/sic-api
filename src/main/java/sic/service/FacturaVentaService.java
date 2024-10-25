package sic.service;

import com.querydsl.core.BooleanBuilder;
import org.springframework.data.domain.Page;
import sic.modelo.*;
import sic.modelo.criteria.BusquedaFacturaVentaCriteria;
import sic.modelo.dto.NuevaFacturaVentaDTO;

import java.math.BigDecimal;
import java.util.List;

public interface FacturaVentaService {

  FacturaVenta construirFacturaVenta(NuevaFacturaVentaDTO nuevaFacturaVentaDTO, long idPedido, long idUsuario);

  TipoDeComprobante[] getTiposDeComprobanteVenta(Long idSucursal, Long idCliente, Long idUsuario);

  List<RenglonFactura> getRenglonesPedidoParaFacturar(long idPedido, TipoDeComprobante tipoDeComprobante);

  Page<FacturaVenta> buscarFacturaVenta(BusquedaFacturaVentaCriteria criteria, long idUsuarioLoggedIn);

  List<FacturaVenta> guardar(List<FacturaVenta> facturas, long idPedido, List<Recibo> recibos);

  FacturaVenta autorizarFacturaVenta(FacturaVenta fv);

  void asignarRemitoConFactura(Remito remito, long idFactura);

  List<FacturaVenta> getFacturaVentaDelRemito(Remito remito);

  BigDecimal calcularTotalFacturadoVenta(BusquedaFacturaVentaCriteria criteria, long idUsuarioLoggedIn);

  BigDecimal calcularIvaVenta(BusquedaFacturaVentaCriteria criteria, long idUsuarioLoggedIn);

  BigDecimal calcularGananciaTotal(BusquedaFacturaVentaCriteria criteria, long idUsuarioLoggedIn);

  long calcularNumeroFacturaVenta(TipoDeComprobante tipoDeComprobante, long serie, long idSucursal);

  byte[] getReporteFacturaVenta(Factura factura);

  List<FacturaVenta> dividirFactura(FacturaVenta factura, int[] indices);

  void enviarFacturaVentaPorEmail(long idFactura);

  void agregarRenglonesEnFacturaConIVA(FacturaVenta facturaConIVA, int[] indices, List<RenglonFactura> renglones);

  void agregarRenglonesEnFacturaSinIVA(FacturaVenta facturaSinIVA, int[] indices, List<RenglonFactura> renglones);

  BooleanBuilder getBuilderVenta(BusquedaFacturaVentaCriteria criteria);

  List<FacturaVenta> getFacturasVentaPorId(long[] idFactura);
}