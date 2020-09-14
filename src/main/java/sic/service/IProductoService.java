package sic.service;

import java.math.BigDecimal;

import com.querydsl.core.BooleanBuilder;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import sic.modelo.*;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import sic.modelo.criteria.BusquedaProductoCriteria;
import sic.modelo.dto.ProductoFaltanteDTO;
import sic.modelo.dto.NuevoProductoDTO;
import sic.modelo.dto.ProductosParaActualizarDTO;
import sic.modelo.dto.ProductosParaVerificarStockDTO;

public interface IProductoService {

  void actualizar(Producto productoPorActualizar, Producto productoPersistido, byte[] imagen);

  Pedido devolverStockPedido(
      Pedido pedido, TipoDeOperacion tipoDeOperacion, List<RenglonPedido> renglonesAnteriores, Long idSucursalOrigen);

  Pedido actualizarStockPedido(Pedido pedido, TipoDeOperacion tipoDeOperacion);

  void actualizarStockFacturaCompra(
      Map<Long, BigDecimal> idsYCantidades,
      Long idSucursal,
      TipoDeOperacion operacion,
      Movimiento movimiento);

  void actualizarStockNotaCredito(
          Map<Long, BigDecimal> idsYCantidades, Long idSucursal, TipoDeOperacion operacion, Movimiento movimiento);

  void actualizarStockTraspaso(Traspaso traspaso, TipoDeOperacion tipoDeOperacion);

  void validarReglasDeNegocio(TipoDeOperacion operacion, Producto producto);

  void validarCalculos(Producto producto);

  Page<Producto> buscarProductos(BusquedaProductoCriteria criteria);

  BooleanBuilder getBuilder(BusquedaProductoCriteria criteria);

  List<Producto> buscarProductosParaReporte(BusquedaProductoCriteria criteria);

  BigDecimal calcularGananciaNeto(BigDecimal precioCosto, BigDecimal gananciaPorcentaje);

  List<ProductoFaltanteDTO> getProductosSinStockDisponible(
      ProductosParaVerificarStockDTO productosParaVerificarStockDTO);

  BigDecimal calcularGananciaPorcentaje(
      BigDecimal precioDeListaNuevo,
      BigDecimal precioDeListaAnterior,
      BigDecimal pvp,
      BigDecimal ivaPorcentaje,
      BigDecimal impInternoPorcentaje,
      BigDecimal precioCosto,
      boolean descendente);

  BigDecimal calcularIVANeto(BigDecimal precioCosto, BigDecimal ivaPorcentaje);

  BigDecimal calcularPVP(BigDecimal precioCosto, BigDecimal gananciaPorcentaje);

  BigDecimal calcularPrecioLista(BigDecimal pvp, BigDecimal ivaPorcentaje);

  void eliminarMultiplesProductos(long[] idProducto);

  Producto getProductoPorCodigo(String codigo);

  Producto getProductoPorDescripcion(String descripciona);

  Producto getProductoNoEliminadoPorId(long idProducto);

  Page<Producto> getProductosConPrecioBonificado(Page<Producto> productos);

  BigDecimal calcularValorStock(BusquedaProductoCriteria criteria);

  byte[] getListaDePrecios(List<Producto> productos, String formato);

  byte[] getListaDePreciosEnXls(BusquedaProductoCriteria criteria);

  byte[] getListaDePreciosEnPdf(BusquedaProductoCriteria criteria);

  Producto guardar(NuevoProductoDTO producto, long idMedida, long idRubro, long idProveedor);

  void actualizarMultiples(ProductosParaActualizarDTO productosParaActualizarDTO);

  void guardarCantidadesDeSucursalNueva(Sucursal sucursal);

  String subirImagenProducto(long idProducto, byte[] imagen);

  Pageable getPageable(Integer pagina, String ordenarPor, String sentido, int tamanioPagina);
}
