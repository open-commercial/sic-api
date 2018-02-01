package sic.service;

import java.math.BigDecimal;
import java.util.HashMap;
import sic.modelo.TipoDeOperacion;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import sic.modelo.BusquedaProductoCriteria;
import sic.modelo.Empresa;
import sic.modelo.Medida;
import sic.modelo.Movimiento;
import sic.modelo.Producto;
import sic.modelo.Proveedor;
import sic.modelo.Rubro;

public interface IProductoService {

    void actualizar(Producto producto);

    void actualizarStock(HashMap<Long, Double> idsYCantidades, TipoDeOperacion operacion, Movimiento movimiento);

    Page<Producto> buscarProductos(BusquedaProductoCriteria criteria);

    BigDecimal calcularGanancia_Neto(double precioCosto, double ganancia_porcentaje);

    Map<Double, Producto> getProductosSinStockDisponible(long[] idProducto, double[] cantidad);

    Map<Double, Producto> getProductosNoCumplenCantidadVentaMinima(long[] idProducto, double[] cantidad);
    
    BigDecimal calcularGanancia_Porcentaje(Double precioDeListaNuevo, 
            Double precioDeListaAnterior, double pvp, Double ivaPorcentaje, 
            Double impInternoPorcentaje, double precioCosto, boolean descendente);

    BigDecimal calcularIVA_Neto(double precioCosto, double iva_porcentaje);

    BigDecimal calcularImpInterno_Neto(double precioCosto, double impInterno_porcentaje);

    BigDecimal calcularPVP(double precioCosto, double ganancia_porcentaje);

    BigDecimal calcularPrecioLista(double PVP, double iva_porcentaje, double impInterno_porcentaje);    

    void eliminarMultiplesProductos(long[] idProducto);

    Producto getProductoPorCodigo(String codigo, Empresa empresa);

    Producto getProductoPorDescripcion(String descripcion, Empresa empresa);

    Producto getProductoPorId(long id_Producto);
    
    BigDecimal calcularValorStock(BusquedaProductoCriteria criteria);
  
    byte[] getReporteListaDePreciosPorEmpresa(List<Producto> productos, Empresa empresa);

    Producto guardar(Producto producto);

    List<Producto> modificarMultiplesProductos(long[] idProducto,
                                               boolean checkPrecios,            
                                               Double gananciaNeto,
                                               Double gananciaPorcentaje,
                                               Double impuestoInternoNeto,
                                               Double impuestoInternoPorcentaje,
                                               Double IVANeto,
                                               Double IVAPorcentaje,
                                               Double precioCosto,
                                               Double precioLista,
                                               Double precioVentaPublico,                                                                     
                                               boolean checkMedida, Medida medida,
                                               boolean checkRubro, Rubro rubro,
                                               boolean checkProveedor, Proveedor proveedor);    
    
}
