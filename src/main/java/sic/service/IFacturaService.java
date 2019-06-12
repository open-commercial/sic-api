package sic.service;

import java.math.BigDecimal;

import sic.modelo.*;

import java.util.List;

import org.springframework.data.domain.Page;

import javax.validation.Valid;

public interface IFacturaService {

  Factura getFacturaNoEliminadaPorId(long idFactura);

  void eliminarFactura(long idFactura);

  List<Factura> getFacturasDelPedido(Long idPedido);

  TipoDeComprobante[] getTipoFacturaCompra(Empresa empresa, Proveedor proveedor);

  TipoDeComprobante[] getTipoFacturaVenta(Empresa empresa, Cliente cliente);

  TipoDeComprobante[] getTiposFacturaSegunEmpresa(Empresa empresa);

  List<RenglonFactura> getRenglonesDeLaFactura(Long idFactura);

  List<RenglonFactura> getRenglonesDeLaFacturaModificadosParaCredito(Long idFactura);

  RenglonFactura getRenglonFactura(Long idRenglonFactura);

  Page<FacturaCompra> buscarFacturaCompra(BusquedaFacturaCompraCriteria criteria);

  Page<FacturaVenta> buscarFacturaVenta(BusquedaFacturaVentaCriteria criteria, long idUsuarioLoggedIn);

  List<FacturaVenta> guardar(@Valid List<FacturaVenta> facturas, Long idPedido, List<Recibo> recibos);

  List<FacturaCompra> guardar(@Valid List<FacturaCompra> facturas);

  FacturaVenta autorizarFacturaVenta(FacturaVenta fv);

  BigDecimal calcularIvaNetoFactura(TipoDeComprobante tipo, BigDecimal[] cantidades,
                                    BigDecimal[] ivaPorcentajeRenglones, BigDecimal[] ivaNetoRenglones, BigDecimal ivaPorcentaje, BigDecimal porcentajeDescuento, BigDecimal porcentajeRecargo);

  BigDecimal calcularTotalFacturadoVenta(BusquedaFacturaVentaCriteria criteria, long idUsuarioLoggedIn);

  BigDecimal calcularTotalFacturadoCompra(BusquedaFacturaCompraCriteria criteria);

  BigDecimal calcularIvaVenta(BusquedaFacturaVentaCriteria criteria, long idUsuarioLoggedIn);

  BigDecimal calcularIvaCompra(BusquedaFacturaCompraCriteria criteria);

  BigDecimal calcularGananciaTotal(BusquedaFacturaVentaCriteria criteria, long idUsuarioLoggedIn);

  BigDecimal calcularIVANetoRenglon(Movimiento movimiento, TipoDeComprobante tipo,
                                    Producto producto, BigDecimal descuentoPorcentaje);

  BigDecimal calcularPrecioUnitario(Movimiento movimiento, TipoDeComprobante tipoDeComprobante, Producto producto);

  long calcularNumeroFacturaVenta(TipoDeComprobante tipoDeComprobante, long serie, long idEmpresa);

  byte[] getReporteFacturaVenta(Factura factura);

  List<FacturaVenta> dividirFactura(FacturaVenta factura, int[] indices);

  List<RenglonFactura> getRenglonesPedidoParaFacturar(long idPedido, TipoDeComprobante tipoDeComprobante);

  boolean pedidoTotalmenteFacturado(Pedido pedido);

  RenglonFactura calcularRenglon(
    TipoDeComprobante tipoDeComprobante,
    Movimiento movimiento,
    BigDecimal cantidad,
    long idProducto,
    BigDecimal descuentoPorcentaje,
    boolean dividiendoRenglonFactura);

  boolean existeFacturaVentaAnteriorSinAutorizar(FacturaVenta facturaVenta);

}
