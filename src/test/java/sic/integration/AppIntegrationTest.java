package sic.integration;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientResponseException;
import sic.model.Caja;
import sic.model.Cliente;
import sic.model.ConfiguracionSucursal;
import sic.model.FacturaCompra;
import sic.model.FacturaVenta;
import sic.model.Gasto;
import sic.model.Medida;
import sic.model.NotaCredito;
import sic.model.NotaDebito;
import sic.model.Pedido;
import sic.model.Producto;
import sic.model.Proveedor;
import sic.model.Recibo;
import sic.model.Rubro;
import sic.model.Sucursal;
import sic.model.Ubicacion;
import sic.model.Usuario;
import sic.modelo.*;
import sic.modelo.RenglonFactura;
import sic.modelo.criteria.*;
import sic.modelo.dto.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(locations = "/application.properties")
class AppIntegrationTest {

  String token;
  final String apiPrefix = "/api/v1";
  static final BigDecimal CIEN = new BigDecimal("100");

  @Autowired TestRestTemplate restTemplate;

  void iniciarSesionComoAdministrador() {
    this.token =
        restTemplate
            .postForEntity(
                apiPrefix + "/login",
                new Credencial("dueño", "dueño123", Aplicacion.SIC_OPS_WEB),
                String.class)
            .getBody();
    assertNotNull(this.token);
  }

  @BeforeEach
  void setup() {
    // Interceptor de RestTemplate para JWT
    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
    interceptors.add(
        (ClientHttpRequestInterceptor)
            (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
              request.getHeaders().set("Authorization", "Bearer " + token);
              return execution.execute(request, body);
            });
    restTemplate.getRestTemplate().setInterceptors(interceptors);
    // ErrorHandler para RestTemplate
    restTemplate
        .getRestTemplate()
        .setErrorHandler(
            new ResponseErrorHandler() {
              @Override
              public boolean hasError(ClientHttpResponse response) throws IOException {
                HttpStatus.Series series = response.getStatusCode().series();
                return (HttpStatus.Series.CLIENT_ERROR.equals(series)
                    || HttpStatus.Series.SERVER_ERROR.equals(series));
              }

              @Override
              public void handleError(ClientHttpResponse response) throws IOException {
                String mensaje = IOUtils.toString(response.getBody(), Charset.defaultCharset());
                throw new RestClientResponseException(
                    mensaje,
                    response.getRawStatusCode(),
                    response.getStatusText(),
                    response.getHeaders(),
                    null,
                    Charset.defaultCharset());
              }
            });
  }

  @Test
  @DisplayName("Iniciar actividad con una nueva sucursal y un usuario Administrador")
  @Order(1)
  void iniciarActividadComercial() throws IOException {
    this.token =
        restTemplate
            .postForEntity(
                apiPrefix + "/login",
                new Credencial("test", "test", Aplicacion.SIC_OPS_WEB),
                String.class)
            .getBody();
    Usuario credencial =
        Usuario.builder()
            .username("dueño")
            .password("dueño123")
            .nombre("Max")
            .apellido("Power")
            .email("liderDeLaEmpresa@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.ADMINISTRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, Usuario.class);
    credencial.setHabilitado(true);
    restTemplate.put(apiPrefix + "/usuarios", credencial);
    this.iniciarSesionComoAdministrador();
    Sucursal sucursal =
        Sucursal.builder()
            .nombre("FirstOfAll")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .email("support@globocorporation.com")
            .idFiscal(30712391215L)
            .ubicacion(Ubicacion.builder().idLocalidad(1L).idProvincia(1L).build())
            .build();
    Sucursal sucursalRecuperada =
        restTemplate.postForObject(apiPrefix + "/sucursales", sucursal, Sucursal.class);
    assertEquals(sucursal, sucursalRecuperada);
    ConfiguracionSucursal configuracionSucursal =
        restTemplate.getForObject(
            apiPrefix + "/configuraciones-sucursal/" + sucursalRecuperada.getIdSucursal(),
            ConfiguracionSucursal.class);
    configuracionSucursal.setPuntoDeRetiro(true);
    restTemplate.put(apiPrefix + "/configuraciones-sucursal", configuracionSucursal);
    configuracionSucursal =
        restTemplate.getForObject(
            apiPrefix + "/configuraciones-sucursal/" + sucursalRecuperada.getIdSucursal(),
            ConfiguracionSucursal.class);
    assertTrue(configuracionSucursal.isPuntoDeRetiro());
    File resource = new ClassPathResource("/certificadoAfipTest.p12").getFile();
    byte[] certificadoAfip = new byte[(int) resource.length()];
    FileInputStream fileInputStream = new FileInputStream(resource);
    fileInputStream.read(certificadoAfip);
    fileInputStream.close();
    this.iniciarSesionComoAdministrador();
    configuracionSucursal.setCertificadoAfip(certificadoAfip);
    configuracionSucursal.setFacturaElectronicaHabilitada(true);
    configuracionSucursal.setFirmanteCertificadoAfip("globo");
    configuracionSucursal.setPasswordCertificadoAfip("globo123");
    configuracionSucursal.setNroPuntoDeVentaAfip(2);
    restTemplate.put(apiPrefix + "/configuraciones-sucursal", configuracionSucursal);
    ConfiguracionSucursal configuracionSucursalActualizada =
        restTemplate.getForObject(
            apiPrefix + "/configuraciones-sucursal/" + sucursalRecuperada.getIdSucursal(),
            ConfiguracionSucursal.class);
    assertEquals(configuracionSucursal, configuracionSucursalActualizada);
  }

  @Test
  @DisplayName("Abrir caja con $1000 en efectivo y registrar un gasto por $500 con transferencia")
  @Order(2)
  void testEscenarioAbrirCaja() {
    this.iniciarSesionComoAdministrador();
    Caja cajaAbierta =
        restTemplate.postForObject(
            apiPrefix + "/cajas/apertura/sucursales/1?saldoApertura=1000", null, Caja.class);
    assertEquals(EstadoCaja.ABIERTA, cajaAbierta.getEstado());
    assertEquals(new BigDecimal("1000"), cajaAbierta.getSaldoApertura());
    Gasto nuevoGasto =
        Gasto.builder().monto(new BigDecimal("500")).concepto("Pago de Agua").build();
    List<Sucursal> sucursales =
        Arrays.asList(restTemplate.getForObject(apiPrefix + "/sucursales", Sucursal[].class));
    assertFalse(sucursales.isEmpty());
    assertEquals(1, sucursales.size());
    restTemplate.postForObject(
        apiPrefix + "/gastos?idFormaDePago=1&idSucursal=" + sucursales.get(0).getIdSucursal(),
        nuevoGasto,
        Gasto.class);
    BusquedaGastoCriteria criteria = BusquedaGastoCriteria.builder().idSucursal(1L).build();
    HttpEntity<BusquedaGastoCriteria> requestEntity = new HttpEntity<>(criteria);
    PaginaRespuestaRest<Gasto> resultadoBusqueda =
        restTemplate
            .exchange(
                apiPrefix + "/gastos/busqueda/criteria",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<PaginaRespuestaRest<Gasto>>() {})
            .getBody();
    assertNotNull(resultadoBusqueda);
    List<Gasto> gastosRecuperados = resultadoBusqueda.getContent();
    assertEquals(1, gastosRecuperados.size());
    assertEquals(new BigDecimal("500.000000000000000"), gastosRecuperados.get(0).getMonto());
    assertEquals("Pago de Agua", gastosRecuperados.get(0).getConcepto());
  }

  @Test
  @DisplayName(
      "Comprar productos al proveedor RI con factura A y verificar saldo CC, luego saldar la CC con un cheque de 3ro")
  @Order(3)
  void testEscenarioCompraEscenario1() {
    this.iniciarSesionComoAdministrador();
    Proveedor proveedor =
        Proveedor.builder()
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .razonSocial("Chamaco S.R.L.")
            .build();
    Proveedor proveedorRecuperado =
        restTemplate.postForObject(apiPrefix + "/proveedores", proveedor, Proveedor.class);
    assertEquals(proveedor, proveedorRecuperado);
    Rubro rubro = Rubro.builder().nombre("Ferreteria").build();
    Rubro rubroDadoDeAlta = restTemplate.postForObject(apiPrefix + "/rubros", rubro, Rubro.class);
    assertEquals(rubro, rubroDadoDeAlta);
    Medida medida = Medida.builder().nombre("Metro").build();
    Medida medidaDadaDeAlta =
        restTemplate.postForObject(apiPrefix + "/medidas", medida, Medida.class);
    assertEquals(medida, medidaDadaDeAlta);
    NuevoProductoDTO nuevoProductoUno =
        NuevoProductoDTO.builder()
            .descripcion("Ventilador de pie")
            .cantidadEnSucursal(
                new HashMap<Long, BigDecimal>() {
                  {
                    put(1L, BigDecimal.TEN);
                  }
                })
            .bulto(BigDecimal.ONE)
            .precioCosto(CIEN)
            .gananciaPorcentaje(new BigDecimal("900"))
            .gananciaNeto(new BigDecimal("900"))
            .precioVentaPublico(new BigDecimal("1000"))
            .ivaPorcentaje(new BigDecimal("21.0"))
            .ivaNeto(new BigDecimal("210"))
            .precioLista(new BigDecimal("1210"))
            .porcentajeBonificacionPrecio(new BigDecimal("20"))
            .build();
    NuevoProductoDTO nuevoProductoDos =
        NuevoProductoDTO.builder()
            .descripcion("Reflector led 100w")
            .cantidadEnSucursal(
                new HashMap<Long, BigDecimal>() {
                  {
                    put(1L, new BigDecimal("6"));
                  }
                })
            .bulto(BigDecimal.ONE)
            .precioCosto(CIEN)
            .gananciaPorcentaje(new BigDecimal("900"))
            .gananciaNeto(new BigDecimal("900"))
            .precioVentaPublico(new BigDecimal("1000"))
            .ivaPorcentaje(new BigDecimal("10.5"))
            .ivaNeto(new BigDecimal("105"))
            .precioLista(new BigDecimal("1105"))
            .porcentajeBonificacionPrecio(new BigDecimal("20"))
            .build();
    NuevoProductoDTO nuevoProductoTres =
            NuevoProductoDTO.builder()
                    .descripcion("Canilla Monocomando")
                    .cantidadEnSucursal(
                            new HashMap<Long, BigDecimal>() {
                              {
                                put(1L, new BigDecimal("10"));
                              }
                            })
                    .bulto(BigDecimal.ONE)
                    .precioCosto(new BigDecimal("10859.73"))
                    .gananciaPorcentaje(new BigDecimal("11.37"))
                    .gananciaNeto(new BigDecimal("1234.751"))
                    .precioVentaPublico(new BigDecimal("12094.481"))
                    .ivaPorcentaje(new BigDecimal("10.5"))
                    .ivaNeto(new BigDecimal("1269.921"))
                    .precioLista(new BigDecimal("13364.402"))
                    .porcentajeBonificacionPrecio(BigDecimal.TEN)
                    .build();
    Sucursal sucursal =
        restTemplate.getForObject(apiPrefix + "/sucursales/1", Sucursal.class);
    Producto productoUnoRecuperado =
        restTemplate.postForObject(
            apiPrefix
                + "/productos?idMedida="
                + medidaDadaDeAlta.getIdMedida()
                + "&idRubro="
                + rubroDadoDeAlta.getIdRubro()
                + "&idProveedor="
                + proveedorRecuperado.getIdProveedor()
                + "&idSucursal="
                + sucursal.getIdSucursal(),
            nuevoProductoUno,
            Producto.class);
    assertEquals("Ventilador de pie", productoUnoRecuperado.getDescripcion());
    assertEquals(BigDecimal.TEN, productoUnoRecuperado.getCantidadTotalEnSucursales());
    assertEquals("Metro", productoUnoRecuperado.getNombreMedida());
    assertEquals(new BigDecimal("100"), productoUnoRecuperado.getPrecioCosto());
    assertEquals(new BigDecimal("900"), productoUnoRecuperado.getGananciaPorcentaje());
    assertEquals(new BigDecimal("900"), productoUnoRecuperado.getGananciaNeto());
    assertEquals(new BigDecimal("1000"), productoUnoRecuperado.getPrecioVentaPublico());
    assertEquals(new BigDecimal("21.0"), productoUnoRecuperado.getIvaPorcentaje());
    assertEquals(new BigDecimal("210"), productoUnoRecuperado.getIvaNeto());
    assertEquals(new BigDecimal("1210"), productoUnoRecuperado.getPrecioLista());
    assertEquals("Ferreteria", productoUnoRecuperado.getNombreRubro());
    assertEquals(BigDecimal.ZERO, productoUnoRecuperado.getPorcentajeBonificacionOferta());
    assertEquals(new BigDecimal("20"), productoUnoRecuperado.getPorcentajeBonificacionPrecio());
    assertEquals(
        new BigDecimal("968.000000000000000"), productoUnoRecuperado.getPrecioBonificado());
    ProductoDTO productoDosRecuperado =
        restTemplate.postForObject(
            apiPrefix
                + "/productos?idMedida="
                + medidaDadaDeAlta.getIdMedida()
                + "&idRubro="
                + rubroDadoDeAlta.getIdRubro()
                + "&idProveedor="
                + proveedorRecuperado.getIdProveedor()
                + "&idSucursal="
                + sucursal.getIdSucursal(),
            nuevoProductoDos,
            ProductoDTO.class);
    assertEquals("Reflector led 100w", productoDosRecuperado.getDescripcion());
    assertEquals(new BigDecimal("6"), productoDosRecuperado.getCantidadTotalEnSucursales());
    assertEquals("Metro", productoDosRecuperado.getNombreMedida());
    assertEquals(new BigDecimal("100"), productoDosRecuperado.getPrecioCosto());
    assertEquals(new BigDecimal("900"), productoDosRecuperado.getGananciaPorcentaje());
    assertEquals(new BigDecimal("900"), productoDosRecuperado.getGananciaNeto());
    assertEquals(new BigDecimal("1000"), productoDosRecuperado.getPrecioVentaPublico());
    assertEquals(new BigDecimal("10.5"), productoDosRecuperado.getIvaPorcentaje());
    assertEquals(new BigDecimal("105"), productoDosRecuperado.getIvaNeto());
    assertEquals(new BigDecimal("1105"), productoDosRecuperado.getPrecioLista());
    assertEquals("Ferreteria", productoDosRecuperado.getNombreRubro());
    assertEquals(BigDecimal.ZERO, productoDosRecuperado.getPorcentajeBonificacionOferta());
    assertEquals(new BigDecimal("20"), productoDosRecuperado.getPorcentajeBonificacionPrecio());
    assertEquals(
        new BigDecimal("884.000000000000000"), productoDosRecuperado.getPrecioBonificado());
    ProductoDTO productoTresRecuperado =
            restTemplate.postForObject(
                    apiPrefix
                            + "/productos?idMedida="
                            + medidaDadaDeAlta.getIdMedida()
                            + "&idRubro="
                            + rubroDadoDeAlta.getIdRubro()
                            + "&idProveedor="
                            + proveedorRecuperado.getIdProveedor()
                            + "&idSucursal="
                            + sucursal.getIdSucursal(),
                    nuevoProductoTres,
                    ProductoDTO.class);
    assertEquals("Canilla Monocomando", productoTresRecuperado.getDescripcion());
    assertEquals(BigDecimal.TEN, productoTresRecuperado.getCantidadTotalEnSucursales());
    assertEquals("Metro", productoTresRecuperado.getNombreMedida());
    assertEquals(new BigDecimal("10859.73"), productoTresRecuperado.getPrecioCosto());
    assertEquals(new BigDecimal("11.37"), productoTresRecuperado.getGananciaPorcentaje());
    assertEquals(new BigDecimal("1234.751"), productoTresRecuperado.getGananciaNeto());
    assertEquals(new BigDecimal("12094.481"), productoTresRecuperado.getPrecioVentaPublico());
    assertEquals(new BigDecimal("10.5"), productoTresRecuperado.getIvaPorcentaje());
    assertEquals(new BigDecimal("1269.921"), productoTresRecuperado.getIvaNeto());
    assertEquals(new BigDecimal("13364.402"), productoTresRecuperado.getPrecioLista());
    assertEquals("Ferreteria", productoTresRecuperado.getNombreRubro());
    assertEquals(BigDecimal.ZERO, productoTresRecuperado.getPorcentajeBonificacionOferta());
    assertEquals(BigDecimal.TEN, productoTresRecuperado.getPorcentajeBonificacionPrecio());
    assertEquals(
            new BigDecimal("12027.961800000000000"), productoTresRecuperado.getPrecioBonificado());
    List<NuevoRenglonFacturaDTO> nuevosRenglones = new ArrayList<>();
    NuevoRenglonFacturaDTO nuevoRenglon =
        NuevoRenglonFacturaDTO.builder()
            .cantidad(new BigDecimal("4"))
            .idProducto(productoUnoRecuperado.getIdProducto())
            .bonificacion(new BigDecimal("20"))
            .build();
    nuevosRenglones.add(nuevoRenglon);
    nuevoRenglon =
        NuevoRenglonFacturaDTO.builder()
            .cantidad(new BigDecimal("3"))
            .idProducto(productoDosRecuperado.getIdProducto())
            .bonificacion(new BigDecimal("20"))
            .build();
    nuevosRenglones.add(nuevoRenglon);
    NuevaFacturaCompraDTO nuevaFacturaCompraDTO =
        NuevaFacturaCompraDTO.builder()
            .idProveedor(1L)
            .idSucursal(1L)
            .tipoDeComprobante(TipoDeComprobante.FACTURA_A)
            .renglones(nuevosRenglones)
            .recargoPorcentaje(BigDecimal.TEN)
            .descuentoPorcentaje(new BigDecimal("25"))
            .fecha(LocalDateTime.now())
            .build();
    restTemplate.postForObject(
        apiPrefix + "/facturas/compras", nuevaFacturaCompraDTO, FacturaCompra[].class);
    BusquedaFacturaCompraCriteria criteria =
        BusquedaFacturaCompraCriteria.builder()
            .idSucursal(1L)
            .tipoComprobante(TipoDeComprobante.FACTURA_A)
            .build();
    HttpEntity<BusquedaFacturaCompraCriteria> requestEntity = new HttpEntity<>(criteria);
    PaginaRespuestaRest<FacturaCompra> resultadoBusqueda =
        restTemplate
            .exchange(
                apiPrefix + "/facturas/compras/busqueda/criteria",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<PaginaRespuestaRest<FacturaCompra>>() {})
            .getBody();
    assertNotNull(resultadoBusqueda);
    List<FacturaCompra> facturasRecuperadas = resultadoBusqueda.getContent();
    assertEquals(1, facturasRecuperadas.size());
    assertEquals(new BigDecimal("560.0"), facturasRecuperadas.get(0).getSubTotal());
    assertEquals(new BigDecimal("56.0"), facturasRecuperadas.get(0).getRecargoNeto());
    assertEquals(
        new BigDecimal("140.0"), facturasRecuperadas.get(0).getDescuentoNeto());
    assertEquals(
        new BigDecimal("476.0"), facturasRecuperadas.get(0).getSubTotalBruto());
    assertEquals(
        new BigDecimal("21.42"),
        facturasRecuperadas.get(0).getIva105Neto());
    assertEquals(
        new BigDecimal("57.12"),
        facturasRecuperadas.get(0).getIva21Neto());
    assertEquals(
        new BigDecimal("554.54"),
        facturasRecuperadas.get(0).getTotal());
    assertEquals(
        proveedorRecuperado.getRazonSocial(), facturasRecuperadas.get(0).getRazonSocialProveedor());
    assertEquals(sucursal.getNombre(), facturasRecuperadas.get(0).getNombreSucursal());
    assertEquals(
        new BigDecimal("-554.540000000000000"),
        restTemplate.getForObject(
            apiPrefix + "/cuentas-corriente/proveedores/1/saldo", BigDecimal.class));
    Recibo recibo =
        Recibo.builder()
            .monto(554.54)
            .concepto("Recibo para proveedor")
            .idSucursal(sucursal.getIdSucursal())
            .idProveedor(proveedorRecuperado.getIdProveedor())
            .idFormaDePago(2L)
            .build();
    Recibo reciboRecuperado =
        restTemplate.postForObject(apiPrefix + "/recibos/proveedores", recibo, Recibo.class);
    assertEquals(recibo, reciboRecuperado);
    assertEquals(
        0.0,
        restTemplate
            .getForObject(apiPrefix + "/cuentas-corriente/proveedores/1/saldo", BigDecimal.class)
            .doubleValue());
  }

  @Test
  @DisplayName("Actualizar CC segun ND por mora, luego verificar saldo CC")
  @Order(4)
  void testEscenarioNotaDebito() {
    this.iniciarSesionComoAdministrador();
    BusquedaProveedorCriteria criteriaParaProveedores = BusquedaProveedorCriteria.builder().build();
    HttpEntity<BusquedaProveedorCriteria> requestEntityParaProveedores =
        new HttpEntity<>(criteriaParaProveedores);
    PaginaRespuestaRest<Proveedor> resultadoBusquedaProveedor =
        restTemplate
            .exchange(
                apiPrefix + "/proveedores/busqueda/criteria",
                HttpMethod.POST,
                requestEntityParaProveedores,
                new ParameterizedTypeReference<PaginaRespuestaRest<Proveedor>>() {})
            .getBody();
    assertNotNull(resultadoBusquedaProveedor);
    List<Proveedor> proveedoresRecuperados = resultadoBusquedaProveedor.getContent();
    assertEquals(1, proveedoresRecuperados.size());
    BusquedaReciboCriteria criteriaParaRecibos =
        BusquedaReciboCriteria.builder()
            .idProveedor(proveedoresRecuperados.get(0).getIdProveedor())
            .build();
    HttpEntity<BusquedaReciboCriteria> requestEntity = new HttpEntity<>(criteriaParaRecibos);
    PaginaRespuestaRest<Recibo> resultadoBusquedaRecibo =
        restTemplate
            .exchange(
                apiPrefix + "/recibos/busqueda/criteria",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<PaginaRespuestaRest<Recibo>>() {})
            .getBody();
    assertNotNull(resultadoBusquedaRecibo);
    List<Recibo> recibosRecuperados = resultadoBusquedaRecibo.getContent();
    assertEquals(1, recibosRecuperados.size());
    NuevaNotaDebitoDeReciboDTO nuevaNotaDebitoDeReciboDTO =
        NuevaNotaDebitoDeReciboDTO.builder()
            .idRecibo(recibosRecuperados.get(0).getIdRecibo())
            .gastoAdministrativo(new BigDecimal("1500.00"))
            .motivo("No pagamos, la vida es así.")
            .tipoDeComprobante(TipoDeComprobante.NOTA_DEBITO_A)
            .build();
    NotaDebito notaDebitoCalculada =
        restTemplate.postForObject(
            apiPrefix + "/notas/debito/calculos", nuevaNotaDebitoDeReciboDTO, NotaDebito.class);
    NotaDebito notaDebitoGuardada =
        restTemplate.postForObject(
            apiPrefix + "/notas/debito", notaDebitoCalculada, NotaDebito.class);
    assertEquals(notaDebitoCalculada, notaDebitoGuardada);
  }

  @Test
  @DisplayName("Dar de alta un producto con imagen")
  @Order(5)
  void testEscenarioAltaDeProductoConImagen() throws IOException {
    this.iniciarSesionComoAdministrador();
    List<Medida> medidas =
        Arrays.asList(restTemplate.getForObject(apiPrefix + "/medidas", Medida[].class));
    assertFalse(medidas.isEmpty());
    assertEquals(1, medidas.size());
    List<Rubro> rubros =
        Arrays.asList(restTemplate.getForObject(apiPrefix + "/rubros", Rubro[].class));
    assertFalse(rubros.isEmpty());
    assertEquals(1, rubros.size());
    List<Sucursal> sucursales =
        Arrays.asList(restTemplate.getForObject(apiPrefix + "/sucursales", Sucursal[].class));
    assertFalse(sucursales.isEmpty());
    assertEquals(1, sucursales.size());
    BusquedaProveedorCriteria criteriaParaProveedores = BusquedaProveedorCriteria.builder().build();
    HttpEntity<BusquedaProveedorCriteria> requestEntityParaProveedores =
        new HttpEntity<>(criteriaParaProveedores);
    PaginaRespuestaRest<Proveedor> resultadoBusquedaProveedor =
        restTemplate
            .exchange(
                apiPrefix + "/proveedores/busqueda/criteria",
                HttpMethod.POST,
                requestEntityParaProveedores,
                new ParameterizedTypeReference<PaginaRespuestaRest<Proveedor>>() {})
            .getBody();
    assertNotNull(resultadoBusquedaProveedor);
    List<Proveedor> proveedoresRecuperados = resultadoBusquedaProveedor.getContent();
    assertEquals(1, proveedoresRecuperados.size());
    BufferedImage bImage = ImageIO.read(getClass().getResource("/imagenProductoTest.jpeg"));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ImageIO.write(bImage, "jpeg", bos);
    NuevoProductoDTO nuevoProductoCuatro =
        NuevoProductoDTO.builder()
            .descripcion("Corta Papas - Vegetales")
            .cantidadEnSucursal(
                new HashMap<Long, BigDecimal>() {
                  {
                    put(1L, BigDecimal.TEN);
                  }
                })
            .bulto(BigDecimal.ONE)
            .precioCosto(CIEN)
            .gananciaPorcentaje(new BigDecimal("900"))
            .gananciaNeto(new BigDecimal("900"))
            .precioVentaPublico(new BigDecimal("1000"))
            .ivaPorcentaje(new BigDecimal("10.5"))
            .ivaNeto(new BigDecimal("105"))
            .precioLista(new BigDecimal("1105"))
            .porcentajeBonificacionPrecio(new BigDecimal("20"))
            .publico(true)
            .imagen(bos.toByteArray())
            .build();
    Producto productoConImagen =
        restTemplate.postForObject(
            apiPrefix
                + "/productos?idMedida="
                + medidas.get(0).getIdMedida()
                + "&idRubro="
                + rubros.get(0).getIdRubro()
                + "&idProveedor="
                + proveedoresRecuperados.get(0).getIdProveedor(),
            nuevoProductoCuatro,
            Producto.class);
    assertNotNull(productoConImagen.getUrlImagen());
  }

  @Test
  @DisplayName("Dar de alta un cliente y levantar un pedido")
  @Order(6)
  void testEscenarioAltaClienteYPedido() {
    this.iniciarSesionComoAdministrador();
    Usuario credencial =
        Usuario.builder()
            .username("elenanocanete")
            .password("siempredebarrio")
            .nombre("Juan")
            .apellido("Canete")
            .email("caniete@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    Usuario credencialDadaDeAlta =
        restTemplate.postForObject(apiPrefix + "/usuarios", credencial, Usuario.class);
    assertEquals(credencial, credencialDadaDeAlta);
    Cliente cliente =
        Cliente.builder()
            .montoCompraMinima(new BigDecimal("500"))
            .nombreFiscal("Juan Fernando Canete")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(30703176840L)
            .telefono("3785663322")
            .idCredencial(credencialDadaDeAlta.getIdUsuario())
            .ubicacionFacturacion(Ubicacion.builder().idProvincia(1L).idLocalidad(1L).build())
            .email("correoparapagos@gmail.com")
            .build();
    Cliente clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, Cliente.class);
    assertEquals(cliente, clienteRecuperado);
    List<NuevoRenglonPedidoDTO> renglonesPedidoDTO = new ArrayList<>();
    renglonesPedidoDTO.add(
        NuevoRenglonPedidoDTO.builder()
            .idProductoItem(1L)
            .cantidad(new BigDecimal("5.000000000000000"))
            .build());
    renglonesPedidoDTO.add(
        NuevoRenglonPedidoDTO.builder()
            .idProductoItem(2L)
            .cantidad(new BigDecimal("2.000000000000000"))
            .build());
    PedidoDTO pedidoDTO =
        PedidoDTO.builder()
            .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
            .recargoPorcentaje(new BigDecimal("5"))
            .renglones(renglonesPedidoDTO)
            .idSucursal(1L)
            .idCliente(clienteRecuperado.getIdCliente())
            .tipoDeEnvio(TipoDeEnvio.RETIRO_EN_SUCURSAL)
            .build();
    Pedido pedidoRecuperado =
        restTemplate.postForObject(apiPrefix + "/pedidos", pedidoDTO, Pedido.class);
    assertEquals(new BigDecimal("5947.200000000000000"), pedidoRecuperado.getTotalEstimado());
    assertEquals(EstadoPedido.ABIERTO, pedidoRecuperado.getEstado());
    List<sic.model.RenglonPedido> renglonesDelPedido =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix + "/pedidos/" + pedidoRecuperado.getIdPedido() + "/renglones",
                sic.model.RenglonPedido[].class));
    assertEquals(2, renglonesDelPedido.size());
    assertEquals("Ventilador de pie", renglonesDelPedido.get(0).getDescripcionItem());
    assertEquals("Metro", renglonesDelPedido.get(0).getMedidaItem());
    assertEquals(
        new BigDecimal("1210.000000000000000"), renglonesDelPedido.get(0).getPrecioUnitario());
    assertEquals(new BigDecimal("5.000000000000000"), renglonesDelPedido.get(0).getCantidad());
    assertEquals(
        new BigDecimal("20.000000000000000"),
        renglonesDelPedido.get(0).getBonificacionPorcentaje());
    assertEquals(
        new BigDecimal("242.000000000000000"), renglonesDelPedido.get(0).getBonificacionNeta());
    assertEquals(
        new BigDecimal("6050.000000000000000000000000000000"),
        renglonesDelPedido.get(0).getImporteAnterior());
    assertEquals(
        new BigDecimal("4840.000000000000000000000000000000"),
        renglonesDelPedido.get(0).getImporte());
    assertEquals("Reflector led 100w", renglonesDelPedido.get(1).getDescripcionItem());
    assertEquals("Metro", renglonesDelPedido.get(1).getMedidaItem());
    assertEquals(
        new BigDecimal("1105.000000000000000"), renglonesDelPedido.get(1).getPrecioUnitario());
    assertEquals(new BigDecimal("2.000000000000000"), renglonesDelPedido.get(1).getCantidad());
    assertEquals(
        new BigDecimal("20.000000000000000"),
        renglonesDelPedido.get(1).getBonificacionPorcentaje());
    assertEquals(
        new BigDecimal("221.000000000000000"), renglonesDelPedido.get(1).getBonificacionNeta());
    assertEquals(
        new BigDecimal("2210.000000000000000000000000000000"),
        renglonesDelPedido.get(1).getImporteAnterior());
    assertEquals(
        new BigDecimal("1768.000000000000000000000000000000"),
        renglonesDelPedido.get(1).getImporte());
  }

  @Test
  @DisplayName(
          "Modificar el pedido agregando un nuevo producto y cambiando la cantidad de uno ya existente")
  @Order(7)
  void testEscenarioModificacionPedido() {
    this.iniciarSesionComoAdministrador();
    BusquedaPedidoCriteria criteria = BusquedaPedidoCriteria.builder().idSucursal(1L).build();
    HttpEntity<BusquedaPedidoCriteria> requestEntity = new HttpEntity<>(criteria);
    PaginaRespuestaRest<Pedido> resultadoBusquedaPedido =
        restTemplate
            .exchange(
                apiPrefix + "/pedidos/busqueda/criteria",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<PaginaRespuestaRest<Pedido>>() {})
            .getBody();
    assertNotNull(resultadoBusquedaPedido);
    List<Pedido> pedidosRecuperados = resultadoBusquedaPedido.getContent();
    assertEquals(1, pedidosRecuperados.size());
    List<sic.model.RenglonPedido> renglonesPedidos =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix + "/pedidos/" + pedidosRecuperados.get(0).getIdPedido() + "/renglones",
                sic.model.RenglonPedido[].class));
    assertNotNull(renglonesPedidos);
    assertEquals(2, renglonesPedidos.size());
    List<NuevoRenglonPedidoDTO> renglonesPedidoDTO = new ArrayList<>();
    renglonesPedidos.forEach(
        renglonPedido ->
            renglonesPedidoDTO.add(
                NuevoRenglonPedidoDTO.builder()
                    .idProductoItem(renglonPedido.getIdProductoItem())
                    .cantidad(renglonPedido.getCantidad())
                    .build()));
    renglonesPedidoDTO.get(1).setCantidad(new BigDecimal("3"));
    renglonesPedidoDTO.add(
        NuevoRenglonPedidoDTO.builder().idProductoItem(3L).cantidad(BigDecimal.TEN).build());
    PedidoDTO pedidoDTO =
        PedidoDTO.builder()
            .idPedido(pedidosRecuperados.get(0).getIdPedido())
            .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
            .recargoPorcentaje(new BigDecimal("5"))
            .renglones(renglonesPedidoDTO)
            .idSucursal(1L)
            .idCliente(pedidosRecuperados.get(0).getCliente().getIdCliente())
            .tipoDeEnvio(TipoDeEnvio.RETIRO_EN_SUCURSAL)
            .build();
    restTemplate.put(apiPrefix + "/pedidos", pedidoDTO);
    criteria = BusquedaPedidoCriteria.builder().idSucursal(1L).build();
    requestEntity = new HttpEntity<>(criteria);
    resultadoBusquedaPedido =
            restTemplate
                    .exchange(
                            apiPrefix + "/pedidos/busqueda/criteria",
                            HttpMethod.POST,
                            requestEntity,
                            new ParameterizedTypeReference<PaginaRespuestaRest<Pedido>>() {})
                    .getBody();
    assertNotNull(resultadoBusquedaPedido);
    pedidosRecuperados = resultadoBusquedaPedido.getContent();
    assertEquals(1, pedidosRecuperados.size());
    assertEquals(EstadoPedido.ABIERTO, pedidosRecuperados.get(0).getEstado());
    List<sic.model.RenglonPedido> renglonesDelPedido =
            Arrays.asList(
                    restTemplate.getForObject(
                            apiPrefix + "/pedidos/" + pedidosRecuperados.get(0).getIdPedido() + "/renglones",
                            sic.model.RenglonPedido[].class));
    assertEquals(3, renglonesDelPedido.size());
    assertEquals("Ventilador de pie", renglonesDelPedido.get(0).getDescripcionItem());
    assertEquals("Metro", renglonesDelPedido.get(0).getMedidaItem());
    assertEquals(new BigDecimal("1210.000000000000000"), renglonesDelPedido.get(0).getPrecioUnitario());
    assertEquals(new BigDecimal("5.000000000000000"), renglonesDelPedido.get(0).getCantidad());
    assertEquals(new BigDecimal("20.000000000000000"), renglonesDelPedido.get(0).getBonificacionPorcentaje());
    assertEquals(new BigDecimal("242.000000000000000"), renglonesDelPedido.get(0).getBonificacionNeta());
    assertEquals(new BigDecimal("6050.000000000000000000000000000000"), renglonesDelPedido.get(0).getImporteAnterior());
    assertEquals(new BigDecimal("4840.000000000000000000000000000000"), renglonesDelPedido.get(0).getImporte());
    assertEquals("Reflector led 100w", renglonesDelPedido.get(1).getDescripcionItem());
    assertEquals("Metro", renglonesDelPedido.get(1).getMedidaItem());
    assertEquals(new BigDecimal("1105.000000000000000"), renglonesDelPedido.get(1).getPrecioUnitario());
    assertEquals(new BigDecimal("3.000000000000000"), renglonesDelPedido.get(1).getCantidad());
    assertEquals(new BigDecimal("20.000000000000000"), renglonesDelPedido.get(1).getBonificacionPorcentaje());
    assertEquals(new BigDecimal("221.000000000000000"), renglonesDelPedido.get(1).getBonificacionNeta());
    assertEquals(new BigDecimal("3315.000000000000000000000000000000"), renglonesDelPedido.get(1).getImporteAnterior());
    assertEquals(new BigDecimal("2652.000000000000000000000000000000"), renglonesDelPedido.get(1).getImporte());
    assertEquals("Canilla Monocomando", renglonesDelPedido.get(2).getDescripcionItem());
    assertEquals("Metro", renglonesDelPedido.get(2).getMedidaItem());
    assertEquals(new BigDecimal("13364.402000000000000"), renglonesDelPedido.get(2).getPrecioUnitario());
    assertEquals(new BigDecimal("10.000000000000000"), renglonesDelPedido.get(2).getCantidad());
    assertEquals(new BigDecimal("10.000000000000000"), renglonesDelPedido.get(2).getBonificacionPorcentaje());
    assertEquals(new BigDecimal("1336.440200000000000"), renglonesDelPedido.get(2).getBonificacionNeta());
    assertEquals(new BigDecimal("133644.020000000000000000000000000000"), renglonesDelPedido.get(2).getImporteAnterior());
    assertEquals(new BigDecimal("120279.618000000000000000000000000000"), renglonesDelPedido.get(2).getImporte());
  }

  @Test
  @DisplayName(
      "Facturar pedido al cliente RI con factura dividida, luego saldar la CC con efectivo")
  @Order(8)
  void testEscenarioVenta1() {
    this.iniciarSesionComoAdministrador();
    BusquedaPedidoCriteria criteria = BusquedaPedidoCriteria.builder().idSucursal(1L).build();
    HttpEntity<BusquedaPedidoCriteria> requestEntity = new HttpEntity<>(criteria);
    PaginaRespuestaRest<Pedido> resultadoBusquedaPedido =
        restTemplate
            .exchange(
                apiPrefix + "/pedidos/busqueda/criteria",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<PaginaRespuestaRest<Pedido>>() {})
            .getBody();
    assertNotNull(resultadoBusquedaPedido);
    List<Pedido> pedidosRecuperados = resultadoBusquedaPedido.getContent();
    assertEquals(1, pedidosRecuperados.size());
    List<RenglonFactura> renglones =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix
                    + "/facturas/ventas/renglones/pedidos/"
                    + pedidosRecuperados.get(0).getIdPedido()
                    + "?tipoDeComprobante=FACTURA_A",
                RenglonFactura[].class));
    List<NuevoRenglonFacturaDTO> nuevosRenglones = new ArrayList<>();
    renglones.forEach(
        renglonFactura ->
            nuevosRenglones.add(
                NuevoRenglonFacturaDTO.builder()
                    .cantidad(renglonFactura.getCantidad())
                    .idProducto(renglonFactura.getIdProductoItem())
                    .build()));
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/1", Cliente.class);
    assertNotNull(cliente);
    int[] indices = new int[] {0};
    NuevaFacturaVentaDTO nuevaFacturaVentaDTO =
        NuevaFacturaVentaDTO.builder()
            .idCliente(1L)
            .idSucursal(1L)
            .idPedido(pedidosRecuperados.get(0).getIdPedido())
            .tipoDeComprobante(TipoDeComprobante.FACTURA_A)
            .renglones(nuevosRenglones)
            .recargoPorcentaje(new BigDecimal("10"))
            .descuentoPorcentaje(new BigDecimal("25"))
            .indices(indices)
            .build();
    FacturaVenta[] facturas =
        restTemplate.postForObject(
            apiPrefix + "/facturas/ventas", nuevaFacturaVentaDTO, FacturaVenta[].class);
    FacturaVenta facturaAutorizada =
        restTemplate.postForObject(
            apiPrefix + "/facturas/ventas/" + facturas[1].getIdFactura() + "/autorizacion",
            null,
            FacturaVenta.class);
    assertNotEquals(0L, facturaAutorizada.getCae());
    assertEquals(2, facturas.length);
    restTemplate.getForObject(apiPrefix + "/facturas/ventas/" + facturas[0].getIdFactura() + "/reporte", byte[].class);
    restTemplate.getForObject(apiPrefix + "/facturas/ventas/" + facturas[1].getIdFactura() + "/reporte", byte[].class);
    assertEquals(cliente.getNombreFiscal(), facturas[0].getNombreFiscalCliente());
    Sucursal sucursal = restTemplate.getForObject(apiPrefix + "/sucursales/1", Sucursal.class);
    assertNotNull(sucursal);
    assertEquals(sucursal.getNombre(), facturas[0].getNombreSucursal());
    assertEquals(cliente.getNombreFiscal(), facturas[1].getNombreFiscalCliente());
    assertEquals(sucursal.getNombre(), facturas[1].getNombreSucursal());
    Usuario credencial = restTemplate.getForObject(apiPrefix + "/usuarios/2", Usuario.class);
    assertNotNull(credencial);
    assertEquals(
        credencial.getNombre()
            + " "
            + credencial.getApellido()
            + " ("
            + credencial.getUsername()
            + ")",
        facturas[0].getNombreUsuario());
    assertEquals(
        credencial.getNombre()
            + " "
            + credencial.getApellido()
            + " ("
            + credencial.getUsername()
            + ")",
        facturas[1].getNombreUsuario());
    assertEquals(new BigDecimal("1600.000000000000000000000000000000"), facturas[0].getSubTotal());
    assertEquals(new BigDecimal("113650.329000000000000000000000000000"), facturas[1].getSubTotal());
    assertEquals(new BigDecimal("160.000000000000000"), facturas[0].getRecargoNeto());
    assertEquals(new BigDecimal("11365.032900000000000"), facturas[1].getRecargoNeto());
    assertEquals(new BigDecimal("400.000000000000000"), facturas[0].getDescuentoNeto());
    assertEquals(new BigDecimal("28412.582250000000000"), facturas[1].getDescuentoNeto());
    assertEquals(BigDecimal.ZERO, facturas[0].getIva105Neto());
    assertEquals(
        new BigDecimal(
            "9929.091863250000000000000000000000000000000000000000000000000000000000000000000"),
        facturas[1].getIva105Neto());
    assertEquals(BigDecimal.ZERO, facturas[0].getIva21Neto());
    assertEquals(
        new BigDecimal("428.400000000000000000000000000000000000000000000000000000000000"),
        facturas[1].getIva21Neto());
    assertEquals(
        new BigDecimal("1360.000000000000000000000000000000"), facturas[0].getSubTotalBruto());
    assertEquals(
        new BigDecimal("96602.779650000000000000000000000000"), facturas[1].getSubTotalBruto());
    assertEquals(new BigDecimal("1360.000000000000000000000000000000"), facturas[0].getTotal());
    assertEquals(
        new BigDecimal(
            "106960.271513250000000000000000000000000000000000000000000000000000000000000000000"),
        facturas[1].getTotal());
    List<RenglonFactura> renglonesFacturaUno =
        Arrays.asList(
            restTemplate.getForObject(apiPrefix + "/facturas/"+ facturas[0].getIdFactura() + "/renglones", RenglonFactura[].class));
    List<RenglonFactura> renglonesFacturaDos =
        Arrays.asList(
            restTemplate.getForObject(apiPrefix + "/facturas/" + facturas[1].getIdFactura() + "/renglones", RenglonFactura[].class));
    assertEquals(2.0, renglonesFacturaUno.get(0).getCantidad().doubleValue());
    assertEquals(3.0, renglonesFacturaDos.get(0).getCantidad().doubleValue());
    assertEquals(3.0, renglonesFacturaDos.get(1).getCantidad().doubleValue());
    assertEquals(10.000000000000000, renglonesFacturaDos.get(2).getCantidad().doubleValue());
    assertEquals(
        -108320.27151325,
        restTemplate
            .getForObject(apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class)
            .doubleValue());
    Recibo recibo =
        Recibo.builder()
            .concepto("Recibo Test")
            .monto(108320.27151325)
            .idSucursal(sucursal.getIdSucursal())
            .idCliente(cliente.getIdCliente())
            .idFormaDePago(1L)
            .build();
    Recibo reciboDeFactura =
        restTemplate.postForObject(apiPrefix + "/recibos/clientes", recibo, Recibo.class);
    assertEquals(recibo, reciboDeFactura);
    assertEquals(
        0.0,
        restTemplate
            .getForObject(apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class)
            .doubleValue());
    criteria = BusquedaPedidoCriteria.builder().idSucursal(1L).build();
    requestEntity = new HttpEntity<>(criteria);
    resultadoBusquedaPedido =
            restTemplate
                    .exchange(
                            apiPrefix + "/pedidos/busqueda/criteria",
                            HttpMethod.POST,
                            requestEntity,
                            new ParameterizedTypeReference<PaginaRespuestaRest<Pedido>>() {})
                    .getBody();
    assertNotNull(resultadoBusquedaPedido);
    pedidosRecuperados = resultadoBusquedaPedido.getContent();
    assertEquals(1, pedidosRecuperados.size());
    assertEquals(EstadoPedido.CERRADO, pedidosRecuperados.get(0).getEstado());
  }

  @Test
  @DisplayName("Realizar devolucion parcial de productos y verificar saldo CC")
  @Order(9)
  void testEscenarioVenta2() {
    this.iniciarSesionComoAdministrador();
    BusquedaFacturaVentaCriteria criteria =
        BusquedaFacturaVentaCriteria.builder()
            .idSucursal(1L)
            .tipoComprobante(TipoDeComprobante.FACTURA_X)
            .numSerie(2L)
            .numFactura(1L)
            .build();
    HttpEntity<BusquedaFacturaVentaCriteria> requestEntity = new HttpEntity<>(criteria);
    PaginaRespuestaRest<FacturaVenta> resultadoBusqueda =
        restTemplate
            .exchange(
                apiPrefix + "/facturas/ventas/busqueda/criteria",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<PaginaRespuestaRest<FacturaVenta>>() {})
            .getBody();
    assertNotNull(resultadoBusqueda);
    List<FacturaVenta> facturasRecuperadas = resultadoBusqueda.getContent();
    Long[] idsRenglonesFactura = new Long[1];
    idsRenglonesFactura[0] = 3L;
    BigDecimal[] cantidades = new BigDecimal[1];
    cantidades[0] = new BigDecimal("1");
    NuevaNotaCreditoDeFacturaDTO nuevaNotaCreditoDTO =
        NuevaNotaCreditoDeFacturaDTO.builder()
            .idFactura(facturasRecuperadas.get(0).getIdFactura())
            .idsRenglonesFactura(idsRenglonesFactura)
            .cantidades(cantidades)
            .modificaStock(true)
            .motivo("No funcionan.")
            .build();
    NotaCredito notaCreditoCalculada =
        restTemplate.postForObject(
            apiPrefix + "/notas/credito/calculos", nuevaNotaCreditoDTO, NotaCredito.class);
    NotaCredito notaCreditoGuardada =
        restTemplate.postForObject(
            apiPrefix + "/notas/credito", notaCreditoCalculada, NotaCredito.class);
    assertEquals(notaCreditoCalculada, notaCreditoGuardada);
    assertEquals(TipoDeComprobante.NOTA_CREDITO_X, notaCreditoGuardada.getTipoComprobante());
    List<sic.model.RenglonNotaCredito> renglones =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix + "/notas/renglones/credito/" + notaCreditoGuardada.getIdNota(),
                sic.model.RenglonNotaCredito[].class));
    assertNotNull(renglones);
    assertEquals(1, renglones.size());
    // renglones
    assertEquals(new BigDecimal("1.000000000000000"), renglones.get(0).getCantidad());
    assertEquals(new BigDecimal("1000.000000000000000"), renglones.get(0).getPrecioUnitario());
    assertEquals(new BigDecimal("20.000000000000000"), renglones.get(0).getDescuentoPorcentaje());
    assertEquals(new BigDecimal("200.000000000000000"), renglones.get(0).getDescuentoNeto());
    assertEquals(new BigDecimal("21.000000000000000"), renglones.get(0).getIvaPorcentaje());
    assertEquals(0.0, renglones.get(0).getIvaNeto().doubleValue());
    assertEquals(new BigDecimal("1000.000000000000000"), renglones.get(0).getImporte());
    assertEquals(new BigDecimal("800.000000000000000"), renglones.get(0).getImporteBruto());
    assertEquals(new BigDecimal("800.000000000000000"), renglones.get(0).getImporteNeto());
    // pie de nota
    assertEquals(
        new BigDecimal("800.000000000000000000000000000000"), notaCreditoGuardada.getSubTotal());
    assertEquals(new BigDecimal("80.000000000000000"), notaCreditoGuardada.getRecargoNeto());
    assertEquals(new BigDecimal("200.000000000000000"), notaCreditoGuardada.getDescuentoNeto());
    assertEquals(
        new BigDecimal("680.000000000000000000000000000000"),
        notaCreditoGuardada.getSubTotalBruto());
    assertEquals(0.0, notaCreditoGuardada.getIva21Neto().doubleValue());
    assertEquals(BigDecimal.ZERO, notaCreditoGuardada.getIva105Neto());
    assertEquals(
        new BigDecimal("680.000000000000000000000000000000"), notaCreditoGuardada.getTotal());
    assertNotNull(restTemplate.getForObject(apiPrefix + "/notas/1/reporte", byte[].class));
    assertEquals(
        680.0,
        restTemplate
            .getForObject(apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class)
            .doubleValue());
  }

  @Test
  @DisplayName("Registrar un cliente nuevo y enviar un pedido mediante carrito de compra")
  @Order(10)
  void testEscenarioRegistracionYPedidoDelNuevoCliente() {
    RegistracionClienteAndUsuarioDTO registro =
        RegistracionClienteAndUsuarioDTO.builder()
            .apellido("Stark")
            .nombre("Sansa")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .email("sansa@got.com")
            .telefono("4157899667")
            .password("caraDeMala")
            .recaptcha("111111")
            .nombreFiscal("theRedWolf")
            .build();
    restTemplate.postForObject(apiPrefix + "/registracion", registro, Void.class);
    this.iniciarSesionComoAdministrador();
    Usuario usuario = restTemplate.getForObject(apiPrefix + "/usuarios/4", Usuario.class);
    assertEquals("Sansa", usuario.getNombre());
    assertEquals("Stark", usuario.getApellido());
    assertTrue(usuario.isHabilitado());
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/2", Cliente.class);
    assertEquals("theRedWolf", cliente.getNombreFiscal());
    assertEquals(0.0, cliente.getMontoCompraMinima().doubleValue());
    sic.model.CuentaCorrienteCliente cuentaCorrienteCliente =
        restTemplate.getForObject(
            apiPrefix + "/cuentas-corriente/clientes/" + cliente.getIdCliente(),
            sic.model.CuentaCorrienteCliente.class);
    assertNotNull(cuentaCorrienteCliente);
    assertEquals(0.0, cuentaCorrienteCliente.getSaldo().doubleValue());
    cliente.setUbicacionFacturacion(Ubicacion.builder().idLocalidad(2L).idProvincia(2L).build());
    restTemplate.put(apiPrefix + "/clientes", cliente);
    this.token =
        restTemplate
            .postForEntity(
                apiPrefix + "/login",
                new Credencial(usuario.getUsername(), "caraDeMala", Aplicacion.SIC_OPS_WEB),
                String.class)
            .getBody();
    assertNotNull(this.token);
    restTemplate.postForObject(
        apiPrefix
            + "/carrito-compra/usuarios/"
            + usuario.getIdUsuario()
            + "/productos/1?cantidad=5",
        null,
        ItemCarritoCompra.class);
    restTemplate.postForObject(
        apiPrefix
            + "/carrito-compra/usuarios/"
            + usuario.getIdUsuario()
            + "/productos/2?cantidad=9",
        null,
        ItemCarritoCompra.class);
    ItemCarritoCompra item1 =
        restTemplate.getForObject(
            apiPrefix + "/carrito-compra/usuarios/" + usuario.getIdUsuario() + "/productos/1",
            ItemCarritoCompra.class);
    assertNotNull(item1);
    assertEquals(1L, item1.getProducto().getIdProducto().longValue());
    assertEquals(5, item1.getCantidad().doubleValue());
    ItemCarritoCompra item2 =
        restTemplate.getForObject(
            apiPrefix + "/carrito-compra/usuarios/4/productos/2", ItemCarritoCompra.class);
    assertNotNull(item2);
    assertEquals(2L, item2.getProducto().getIdProducto().longValue());
    assertEquals(9, item2.getCantidad().doubleValue());
    NuevaOrdenDeCompraDTO nuevaOrdenDeCompraDTO =
        NuevaOrdenDeCompraDTO.builder()
            .idCliente(2L)
            .idUsuario(4L)
            .tipoDeEnvio(TipoDeEnvio.USAR_UBICACION_FACTURACION)
            .observaciones("Por favor comunicarse conmigo antes de facturar.")
            .build();
    Pedido pedido =
        restTemplate.postForObject(
            apiPrefix + "/carrito-compra", nuevaOrdenDeCompraDTO, Pedido.class);
    assertNotNull(pedido);
    assertEquals(14, pedido.getCantidadArticulos().doubleValue());
    assertEquals(
        new BigDecimal("12796.00000000000000000000000000000000000000000000000"),
        pedido.getTotalActual());
    assertEquals(EstadoPedido.ABIERTO, pedido.getEstado());
    List<sic.model.RenglonPedido> renglonesDelPedido =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix + "/pedidos/" + pedido.getIdPedido() + "/renglones",
                sic.model.RenglonPedido[].class));
    assertEquals(2, renglonesDelPedido.size());
    assertEquals("Reflector led 100w", renglonesDelPedido.get(0).getDescripcionItem());
    assertEquals("Metro", renglonesDelPedido.get(0).getMedidaItem());
    assertEquals(
        new BigDecimal("1105.000000000000000"), renglonesDelPedido.get(0).getPrecioUnitario());
    assertEquals(new BigDecimal("9.000000000000000"), renglonesDelPedido.get(0).getCantidad());
    assertEquals(
        new BigDecimal("20.000000000000000"),
        renglonesDelPedido.get(0).getBonificacionPorcentaje());
    assertEquals(
        new BigDecimal("221.000000000000000"), renglonesDelPedido.get(0).getBonificacionNeta());
    assertEquals(
        new BigDecimal("9945.000000000000000000000000000000"),
        renglonesDelPedido.get(0).getImporteAnterior());
    assertEquals(
        new BigDecimal("7956.000000000000000000000000000000"),
        renglonesDelPedido.get(0).getImporte());
    assertEquals("Ventilador de pie", renglonesDelPedido.get(1).getDescripcionItem());
    assertEquals("Metro", renglonesDelPedido.get(1).getMedidaItem());
    assertEquals(
        new BigDecimal("1210.000000000000000"), renglonesDelPedido.get(1).getPrecioUnitario());
    assertEquals(new BigDecimal("5.000000000000000"), renglonesDelPedido.get(1).getCantidad());
    assertEquals(
        new BigDecimal("20.000000000000000"),
        renglonesDelPedido.get(1).getBonificacionPorcentaje());
    assertEquals(
        new BigDecimal("242.000000000000000"), renglonesDelPedido.get(1).getBonificacionNeta());
    assertEquals(
        new BigDecimal("6050.000000000000000000000000000000"),
        renglonesDelPedido.get(1).getImporteAnterior());
    assertEquals(
        new BigDecimal("4840.000000000000000000000000000000"),
        renglonesDelPedido.get(1).getImporte());
  }

  @Test
  @DisplayName("Ingresar dinero a la CC de cliente mediante Mercado Pago")
  @Order(11)
  void testEscenarioAgregarPagoMercadoPago() {
    this.iniciarSesionComoAdministrador();
    Usuario usuario = restTemplate.getForObject(apiPrefix + "/usuarios/4", Usuario.class);
    assertNotNull(usuario);
    this.token =
        restTemplate
            .postForEntity(
                apiPrefix + "/login",
                new Credencial(usuario.getUsername(), "caraDeMala", Aplicacion.SIC_OPS_WEB),
                String.class)
            .getBody();
    assertNotNull(this.token);
    // No se puede probar con tarjeta de credito por no poder generar el token
    NuevoPagoMercadoPagoDTO nuevoPagoMercadoPagoDTO =
        NuevoPagoMercadoPagoDTO.builder()
            .paymentMethodId("pagofacil")
            .installments(1)
            .idCliente(1L)
            .idSucursal(1L)
            .monto(new Float("2000"))
            .build();
    String paymentId =
        restTemplate.postForObject(
            apiPrefix + "/pagos/mercado-pago", nuevoPagoMercadoPagoDTO, String.class);
    // El recibo no se da de alta por ser un pago asincrono.
    restTemplate.postForObject(
        apiPrefix + "/pagos/notificacion?data.id=" + paymentId + "&type=payment", null, void.class);
    assertNotNull(paymentId);
  }

  @Test
  @DisplayName("Cerrar caja y verificar movimientos")
  @Order(12)
  void testEscenarioCerrarCaja1() {
    this.iniciarSesionComoAdministrador();
    List<Sucursal> sucursales =
        Arrays.asList(restTemplate.getForObject(apiPrefix + "/sucursales", Sucursal[].class));
    assertNotNull(sucursales);
    assertEquals(1, sucursales.size());
    BusquedaCajaCriteria criteriaParaBusquedaCaja =
        BusquedaCajaCriteria.builder().idSucursal(sucursales.get(0).getIdSucursal()).build();
    HttpEntity<BusquedaCajaCriteria> requestEntityParaProveedores =
        new HttpEntity<>(criteriaParaBusquedaCaja);
    PaginaRespuestaRest<Caja> resultadosBusquedaCaja =
        restTemplate
            .exchange(
                apiPrefix + "/cajas/busqueda/criteria",
                HttpMethod.POST,
                requestEntityParaProveedores,
                new ParameterizedTypeReference<PaginaRespuestaRest<Caja>>() {})
            .getBody();
    assertNotNull(resultadosBusquedaCaja);
    List<Caja> cajasRecuperadas = resultadosBusquedaCaja.getContent();
    assertEquals(1, cajasRecuperadas.size());
    assertEquals(EstadoCaja.ABIERTA, cajasRecuperadas.get(0).getEstado());
    restTemplate.put(
        apiPrefix + "/cajas/" + cajasRecuperadas.get(0).getIdCaja() + "/cierre?monto=5276.66",
        null);
    resultadosBusquedaCaja =
        restTemplate
            .exchange(
                apiPrefix + "/cajas/busqueda/criteria",
                HttpMethod.POST,
                requestEntityParaProveedores,
                new ParameterizedTypeReference<PaginaRespuestaRest<Caja>>() {})
            .getBody();
    assertNotNull(resultadosBusquedaCaja);
    cajasRecuperadas = resultadosBusquedaCaja.getContent();
    assertEquals(1, cajasRecuperadas.size());
    assertEquals(EstadoCaja.CERRADA, cajasRecuperadas.get(0).getEstado());
    assertEquals(
        new BigDecimal("1000.000000000000000"), cajasRecuperadas.get(0).getSaldoApertura());
    List<MovimientoCaja> movimientoCajas =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix
                    + "/cajas/"
                    + cajasRecuperadas.get(0).getIdCaja()
                    + "/movimientos?idFormaDePago=1",
                MovimientoCaja[].class));
    assertEquals(2, movimientoCajas.size());
    assertEquals(new BigDecimal("108320.271513250000000"), movimientoCajas.get(0).getMonto());
    assertEquals(new BigDecimal("-500.000000000000000"), movimientoCajas.get(1).getMonto());
    movimientoCajas =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix
                    + "/cajas/"
                    + cajasRecuperadas.get(0).getIdCaja()
                    + "/movimientos?idFormaDePago=2",
                MovimientoCaja[].class));
    assertEquals(1, movimientoCajas.size());
    assertEquals(new BigDecimal("-554.540000000000000"), movimientoCajas.get(0).getMonto());
    assertEquals(
        new BigDecimal("108820.271513250000000"),
        restTemplate.getForObject(
            apiPrefix + "/cajas/" + cajasRecuperadas.get(0).getIdCaja() + "/saldo-afecta-caja",
            BigDecimal.class));
    assertEquals(
        new BigDecimal("108265.731513250000000"),
        restTemplate.getForObject(
            apiPrefix + "/cajas/" + cajasRecuperadas.get(0).getIdCaja() + "/saldo-sistema",
            BigDecimal.class));
  }

  @Test
  @DisplayName("Reabrir caja, corregir saldo con un gasto por $750 en efectivo y luego cerrar caja")
  @Order(13)
  void testEscenarioCerrarCaja2() {
    this.iniciarSesionComoAdministrador();
    List<Sucursal> sucursales =
        Arrays.asList(restTemplate.getForObject(apiPrefix + "/sucursales", Sucursal[].class));
    assertEquals(1, sucursales.size());
    BusquedaCajaCriteria criteriaParaBusquedaCaja =
        BusquedaCajaCriteria.builder().idSucursal(sucursales.get(0).getIdSucursal()).build();
    HttpEntity<BusquedaCajaCriteria> requestEntityParaProveedores =
        new HttpEntity<>(criteriaParaBusquedaCaja);
    PaginaRespuestaRest<Caja> resultadosBusquedaCaja =
        restTemplate
            .exchange(
                apiPrefix + "/cajas/busqueda/criteria",
                HttpMethod.POST,
                requestEntityParaProveedores,
                new ParameterizedTypeReference<PaginaRespuestaRest<Caja>>() {})
            .getBody();
    assertNotNull(resultadosBusquedaCaja);
    List<Caja> cajasRecuperadas = resultadosBusquedaCaja.getContent();
    assertEquals(1, cajasRecuperadas.size());
    assertEquals(EstadoCaja.CERRADA, cajasRecuperadas.get(0).getEstado());
    restTemplate.put(
        apiPrefix + "/cajas/" + cajasRecuperadas.get(0).getIdCaja() + "/reapertura?monto=1100",
        null);
    resultadosBusquedaCaja =
        restTemplate
            .exchange(
                apiPrefix + "/cajas/busqueda/criteria",
                HttpMethod.POST,
                requestEntityParaProveedores,
                new ParameterizedTypeReference<PaginaRespuestaRest<Caja>>() {})
            .getBody();
    assertNotNull(resultadosBusquedaCaja);
    cajasRecuperadas = resultadosBusquedaCaja.getContent();
    assertEquals(1, cajasRecuperadas.size());
    assertEquals(EstadoCaja.ABIERTA, cajasRecuperadas.get(0).getEstado());
    assertEquals(
        new BigDecimal("1100.000000000000000"), cajasRecuperadas.get(0).getSaldoApertura());
    Gasto gasto = Gasto.builder().concepto("Gasto olvidado").monto(new BigDecimal("750")).build();
    gasto =
        restTemplate.postForObject(
            apiPrefix + "/gastos?idSucursal=1&idFormaDePago=1", gasto, Gasto.class);
    assertNotNull(gasto);
    restTemplate.put(
        apiPrefix + "/cajas/" + cajasRecuperadas.get(0).getIdCaja() + "/cierre?monto=5276.66",
        null);
    resultadosBusquedaCaja =
        restTemplate
            .exchange(
                apiPrefix + "/cajas/busqueda/criteria",
                HttpMethod.POST,
                requestEntityParaProveedores,
                new ParameterizedTypeReference<PaginaRespuestaRest<Caja>>() {})
            .getBody();
    assertNotNull(resultadosBusquedaCaja);
    cajasRecuperadas = resultadosBusquedaCaja.getContent();
    assertEquals(1, cajasRecuperadas.size());
    assertEquals(EstadoCaja.CERRADA, cajasRecuperadas.get(0).getEstado());
    List<MovimientoCaja> movimientoCajas =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix
                    + "/cajas/"
                    + cajasRecuperadas.get(0).getIdCaja()
                    + "/movimientos?idFormaDePago=1",
                MovimientoCaja[].class));
    assertEquals(3, movimientoCajas.size());
    assertEquals(new BigDecimal("-750.000000000000000"), movimientoCajas.get(0).getMonto());
    assertEquals(new BigDecimal("108320.271513250000000"), movimientoCajas.get(1).getMonto());
    assertEquals(new BigDecimal("-500.000000000000000"), movimientoCajas.get(2).getMonto());
    movimientoCajas =
        Arrays.asList(
            restTemplate.getForObject(
                apiPrefix
                    + "/cajas/"
                    + cajasRecuperadas.get(0).getIdCaja()
                    + "/movimientos?idFormaDePago=2",
                MovimientoCaja[].class));
    assertEquals(1, movimientoCajas.size());
    assertEquals(new BigDecimal("-554.540000000000000"), movimientoCajas.get(0).getMonto());
    assertEquals(
        new BigDecimal("108170.271513250000000"),
        restTemplate.getForObject(
            apiPrefix + "/cajas/" + cajasRecuperadas.get(0).getIdCaja() + "/saldo-afecta-caja",
            BigDecimal.class));
    assertEquals(
        new BigDecimal("107615.731513250000000"),
        restTemplate.getForObject(
            apiPrefix + "/cajas/" + cajasRecuperadas.get(0).getIdCaja() + "/saldo-sistema",
            BigDecimal.class));
  }
}
