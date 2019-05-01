package sic.integration;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientResponseException;
import sic.builder.*;
import sic.modelo.*;
import sic.modelo.dto.*;
import sic.repository.LocalidadRepository;
import sic.repository.ProvinciaRepository;
import sic.repository.UsuarioRepository;
import sic.service.ICajaService;
import sic.service.IClockService;
import sic.service.IPedidoService;
import sic.service.IProductoService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AppIntegrationTest {

  @Autowired
  private UsuarioRepository usuarioRepository;

  @Autowired
  private IProductoService productoService;

  @Autowired
  private IPedidoService pedidoService;

  @Autowired
  private ProvinciaRepository provinciaRepository;

  @Autowired
  private LocalidadRepository localidadRepository;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired private IClockService clockService;

  @Autowired private ICajaService cajaService;

  private String token;

  private final String apiPrefix = "/api/v1";

  private static final BigDecimal IVA_21 = new BigDecimal("21");
  private static final BigDecimal IVA_105 = new BigDecimal("10.5");
  private static final BigDecimal CIEN = new BigDecimal("100");

  @Value("${RECAPTCHA_TEST_KEY}")
  private String recaptchaTestKey;

  private void crearProductos() {
    NuevoProductoDTO productoUno =
      NuevoProductoDTO.builder()
        .codigo(RandomStringUtils.random(10, false, true))
        .descripcion(RandomStringUtils.random(10, true, false))
        .cantidad(BigDecimal.TEN)
        .bulto(BigDecimal.ONE)
        .precioCosto(CIEN)
        .gananciaPorcentaje(new BigDecimal("900"))
        .gananciaNeto(new BigDecimal("900"))
        .precioVentaPublico(new BigDecimal("1000"))
        .ivaPorcentaje(new BigDecimal("21.0"))
        .ivaNeto(new BigDecimal("210"))
        .precioLista(new BigDecimal("1210"))
        .nota("ProductoTest1")
        .publico(true)
        .destacado(true)
        .build();
    NuevoProductoDTO productoDos =
      NuevoProductoDTO.builder()
        .codigo(RandomStringUtils.random(10, false, true))
        .descripcion(RandomStringUtils.random(10, true, false))
        .cantidad(new BigDecimal("6"))
        .bulto(BigDecimal.ONE)
        .precioCosto(CIEN)
        .gananciaPorcentaje(new BigDecimal("900"))
        .gananciaNeto(new BigDecimal("900"))
        .precioVentaPublico(new BigDecimal("1000"))
        .ivaPorcentaje(new BigDecimal("10.5"))
        .ivaNeto(new BigDecimal("105"))
        .precioLista(new BigDecimal("1105"))
        .nota("ProductoTest2")
        .destacado(false)
        .build();
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    RubroDTO rubro = restTemplate.getForObject(apiPrefix + "/rubros/1", RubroDTO.class);
    ProveedorDTO proveedor =
      restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    Medida medida = restTemplate.getForObject(apiPrefix + "/medidas/1", Medida.class);
    restTemplate.postForObject(
      apiPrefix
        + "/productos?idMedida="
        + medida.getId_Medida()
        + "&idRubro="
        + rubro.getId_Rubro()
        + "&idProveedor="
        + proveedor.getId_Proveedor()
        + "&idEmpresa="
        + empresa.getId_Empresa(),
      productoUno,
      ProductoDTO.class);
    restTemplate.postForObject(
      apiPrefix
        + "/productos?idMedida="
        + medida.getId_Medida()
        + "&idRubro="
        + rubro.getId_Rubro()
        + "&idProveedor="
        + proveedor.getId_Proveedor()
        + "&idEmpresa="
        + empresa.getId_Empresa(),
      productoDos,
      ProductoDTO.class);
  }

  private void crearFacturaTipoADePedido() {
    RenglonFactura[] renglonesParaFacturar =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglones/pedidos/1"
          + "?tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A,
        RenglonFactura[].class);
    BigDecimal subTotal = renglonesParaFacturar[0].getImporte();
    assertEquals(
      new BigDecimal("4250.000000000000000000000000000000"),
      renglonesParaFacturar[0].getImporte());
    BigDecimal recargoPorcentaje = BigDecimal.TEN;
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    assertEquals(new BigDecimal("425.000000000000000"), recargo_neto);
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    if (renglonesParaFacturar[0].getIvaPorcentaje().compareTo(IVA_105) == 0) {
      iva_105_netoFactura =
        iva_105_netoFactura.add(
          renglonesParaFacturar[0]
            .getCantidad()
            .multiply(renglonesParaFacturar[0].getIvaNeto()));
    } else if (renglonesParaFacturar[0].getIvaPorcentaje().compareTo(IVA_21) == 0) {
      iva_21_netoFactura =
        iva_21_netoFactura.add(
          renglonesParaFacturar[0]
            .getCantidad()
            .multiply(renglonesParaFacturar[0].getIvaNeto()));
    }
    assertEquals(BigDecimal.ZERO, iva_105_netoFactura);
    assertEquals(
      new BigDecimal("892.500000000000000"),
      iva_21_netoFactura.setScale(15, RoundingMode.HALF_UP));
    BigDecimal subTotalBruto = subTotal.add(recargo_neto);
    assertEquals(new BigDecimal("4675.000000000000000000000000000000"), subTotalBruto);
    BigDecimal total =
      subTotalBruto
        .add(iva_105_netoFactura)
        .add(
          iva_21_netoFactura.multiply(
            BigDecimal.ONE.add(recargoPorcentaje.divide(new BigDecimal("100")))));
    assertEquals(new BigDecimal("5656.750000000000000"), total.setScale(15, RoundingMode.HALF_UP));
    FacturaVentaDTO facturaVentaA = FacturaVentaDTO.builder().build();
    facturaVentaA.setObservaciones("Factura A del Pedido");
    facturaVentaA.setTipoComprobante(TipoDeComprobante.FACTURA_A);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonesParaFacturar[0]);
    facturaVentaA.setRenglones(renglones);
    facturaVentaA.setSubTotal(subTotal);
    facturaVentaA.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaA.setRecargoNeto(recargo_neto);
    facturaVentaA.setDescuentoPorcentaje(BigDecimal.ZERO);
    facturaVentaA.setDescuentoNeto(BigDecimal.ZERO);
    facturaVentaA.setSubTotalBruto(subTotalBruto);
    facturaVentaA.setIva105Neto(iva_105_netoFactura);
    facturaVentaA.setIva21Neto(
      iva_21_netoFactura.multiply(
        BigDecimal.ONE.add(recargoPorcentaje.divide(new BigDecimal("100")))));
    facturaVentaA.setTotal(total);
    facturaVentaA.setFecha(new Date());
    this.abrirCaja();
    restTemplate.postForObject(
      apiPrefix
        + "/facturas/venta?idPedido=1"
        + "&idsFormaDePago=1"
        + "&montos="
        + total
        + "&idCliente=1"
        + "&idEmpresa=1"
        + "&idUsuario=2"
        + "&idTransportista=1",
      facturaVentaA,
      FacturaVenta[].class);
  }

  private void crearFacturaTipoBDePedido() {
    RenglonFactura[] renglonesParaFacturar =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglones/pedidos/1"
          + "?tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B,
        RenglonFactura[].class);
    FacturaVentaDTO facturaVentaB = FacturaVentaDTO.builder().build();
    facturaVentaB.setObservaciones("Factura B del Pedido");
    facturaVentaB.setTipoComprobante(TipoDeComprobante.FACTURA_B);
    List<RenglonFactura> renglonesDeFactura = new ArrayList<>();
    renglonesDeFactura.add(renglonesParaFacturar[0]);
    facturaVentaB.setRenglones(renglonesDeFactura);
    facturaVentaB.setSubTotal(new BigDecimal("2210"));
    facturaVentaB.setRecargoPorcentaje(BigDecimal.TEN);
    facturaVentaB.setRecargoNeto(new BigDecimal("221"));
    facturaVentaB.setDescuentoPorcentaje(BigDecimal.ZERO);
    facturaVentaB.setDescuentoNeto(BigDecimal.ZERO);
    facturaVentaB.setSubTotalBruto(new BigDecimal("2200"));
    facturaVentaB.setIva105Neto(new BigDecimal("231"));
    facturaVentaB.setIva21Neto(BigDecimal.ZERO);
    facturaVentaB.setTotal(new BigDecimal("2431"));
    facturaVentaB.setFecha(new Date());
    restTemplate.postForObject(
      apiPrefix
        + "/facturas/venta?idPedido=1"
        + "&idCliente=1"
        + "&idEmpresa=1"
        + "&idUsuario=2"
        + "&idTransportista=1",
      facturaVentaB,
      FacturaVenta[].class);
  }

  private void crearReciboParaCliente(double monto) {
    ReciboDTO recibo = ReciboDTO.builder().concepto("Recibo Test").monto(monto).build();
    restTemplate.postForObject(
      apiPrefix
        + "/recibos/clientes?"
        + "idUsuario=1"
        + "&idEmpresa=1"
        + "&idCliente=1"
        + "&idFormaDePago=1",
      recibo,
      ReciboDTO.class);
  }

  private void crearNotaDebitoParaCliente() {
    NotaDebitoDTO notaDebitoCliente = new NotaDebitoDTO();
    List<RenglonNotaDebito> renglonesCalculados =
      Arrays.asList(
        restTemplate.getForObject(
          apiPrefix + "/notas/renglon/debito/recibo/1?monto=100&ivaPorcentaje=21",
          RenglonNotaDebito[].class));
    notaDebitoCliente.setRenglonesNotaDebito(renglonesCalculados);
    notaDebitoCliente.setIva105Neto(BigDecimal.ZERO);
    notaDebitoCliente.setIva21Neto(new BigDecimal("21"));
    notaDebitoCliente.setMontoNoGravado(new BigDecimal("5992.5"));
    notaDebitoCliente.setMotivo("Test alta nota debito - Cheque rechazado");
    notaDebitoCliente.setSubTotalBruto(new BigDecimal("100"));
    notaDebitoCliente.setTotal(new BigDecimal("6113.5"));
    restTemplate.postForObject(
      apiPrefix + "/notas/debito/empresa/1/usuario/1/recibo/1?idCliente=1&movimiento=VENTA",
      notaDebitoCliente,
      Nota.class);
    restTemplate.getForObject(apiPrefix + "/notas/1/reporte", byte[].class);
  }

  private void crearNotaCreditoParaCliente() {
    List<FacturaVenta> facturasRecuperadas =
      restTemplate
        .exchange(
          apiPrefix
            + "/facturas/venta/busqueda/criteria?idEmpresa=1"
            + "&tipoFactura="
            + TipoDeComprobante.FACTURA_B
            + "&nroSerie=0"
            + "&nroFactura=1",
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<PaginaRespuestaRest<FacturaVenta>>() {
          })
        .getBody()
        .getContent();
    Long[] idsRenglonesFacura = new Long[]{1L};
    BigDecimal[] cantidades = new BigDecimal[]{new BigDecimal("5")};
    RenglonesDeFacturaParaNotaCreditoDTO renglonesDeFacturaParaNotaCreditoDTO =
      RenglonesDeFacturaParaNotaCreditoDTO.builder()
        .idsRenglonesFactura(idsRenglonesFacura)
        .cantidades(cantidades)
        .tipoDeComprobante(facturasRecuperadas.get(0).getTipoComprobante())
        .build();
    List<RenglonNotaCredito> renglonesNotaCredito =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix
            + "/notas/renglon/credito/producto",
          renglonesDeFacturaParaNotaCreditoDTO,
          RenglonNotaCredito[].class));
    NotaCreditoDTO notaCredito = NotaCreditoDTO.builder().build();
    notaCredito.setRenglonesNotaCredito(renglonesNotaCredito);
    notaCredito.setSubTotal(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/sub-total?importe="
          + renglonesNotaCredito.get(0).getImporteNeto(),
        BigDecimal.class));
    notaCredito.setRecargoPorcentaje(facturasRecuperadas.get(0).getRecargoPorcentaje());
    notaCredito.setRecargoNeto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/recargo-neto?subTotal="
          + notaCredito.getSubTotal()
          + "&recargoPorcentaje="
          + notaCredito.getRecargoPorcentaje(),
        BigDecimal.class));
    notaCredito.setDescuentoPorcentaje(facturasRecuperadas.get(0).getDescuentoPorcentaje());
    notaCredito.setDescuentoNeto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/descuento-neto?subTotal="
          + notaCredito.getSubTotal()
          + "&descuentoPorcentaje="
          + notaCredito.getDescuentoPorcentaje(),
        BigDecimal.class));
    notaCredito.setIva21Neto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/iva-neto?"
          + "tipoDeComprobante="
          + facturasRecuperadas.get(0).getTipoComprobante().name()
          + "&cantidades="
          + renglonesNotaCredito.get(0).getCantidad()
          + "&ivaPorcentajeRenglones="
          + renglonesNotaCredito.get(0).getIvaPorcentaje()
          + "&ivaNetoRenglones="
          + renglonesNotaCredito.get(0).getIvaNeto()
          + "&ivaPorcentaje=21"
          + "&descuentoPorcentaje="
          + facturasRecuperadas.get(0).getDescuentoPorcentaje()
          + "&recargoPorcentaje="
          + facturasRecuperadas.get(0).getRecargoPorcentaje(),
        BigDecimal.class));
    notaCredito.setIva105Neto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/iva-neto?"
          + "tipoDeComprobante="
          + facturasRecuperadas.get(0).getTipoComprobante().name()
          + "&cantidades="
          + renglonesNotaCredito.get(0).getCantidad()
          + "&ivaPorcentajeRenglones="
          + renglonesNotaCredito.get(0).getIvaPorcentaje()
          + "&ivaNetoRenglones="
          + renglonesNotaCredito.get(0).getIvaNeto()
          + "&ivaPorcentaje=10.5"
          + "&descuentoPorcentaje="
          + facturasRecuperadas.get(0).getDescuentoPorcentaje()
          + "&recargoPorcentaje="
          + facturasRecuperadas.get(0).getRecargoPorcentaje(),
        BigDecimal.class));
    notaCredito.setSubTotalBruto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/sub-total-bruto?"
          + "tipoDeComprobante="
          + facturasRecuperadas.get(0).getTipoComprobante().name()
          + "&subTotal="
          + notaCredito.getSubTotal()
          + "&recargoNeto="
          + notaCredito.getRecargoNeto()
          + "&descuentoNeto="
          + notaCredito.getDescuentoNeto()
          + "&iva21Neto="
          + notaCredito.getIva21Neto()
          + "&iva105Neto="
          + notaCredito.getIva105Neto(),
        BigDecimal.class));
    notaCredito.setTotal(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/total?subTotalBruto="
          + notaCredito.getSubTotalBruto()
          + "&iva21Neto="
          + notaCredito.getIva21Neto()
          + "&iva105Neto="
          + notaCredito.getIva105Neto(),
        BigDecimal.class));
    notaCredito.setMotivo("Devolución");
    restTemplate.postForObject(
      apiPrefix
        + "/notas/credito/empresa/1/usuario/1/factura/1?idCliente=1&movimiento=VENTA&modificarStock=true",
      notaCredito,
      Nota.class);
    restTemplate.getForObject(apiPrefix + "/notas/2/reporte", byte[].class);
  }

  private void crearReciboParaProveedor(double monto) {
    ReciboDTO recibo = new ReciboDTO();
    recibo.setMonto(monto);
    recibo.setConcepto("Recibo para proveedor");
    restTemplate.postForObject(
      apiPrefix
        + "/recibos/proveedores?"
        + "idUsuario=2"
        + "&idEmpresa=1"
        + "&idProveedor=1"
        + "&idFormaDePago=1",
      recibo,
      Recibo.class);
  }

  private void crearNotaDebitoParaProveedor() {
    NotaDebitoDTO notaDebito = new NotaDebitoDTO();
    notaDebito.setCAE(0L);
    notaDebito.setFecha(new Date());
    List<RenglonNotaDebito> renglonesCalculados =
      Arrays.asList(
        restTemplate.getForObject(
          apiPrefix + "/notas/renglon/debito/recibo/3?monto=1000&ivaPorcentaje=21",
          RenglonNotaDebito[].class));
    notaDebito.setRenglonesNotaDebito(renglonesCalculados);
    notaDebito.setIva105Neto(BigDecimal.ZERO);
    notaDebito.setIva21Neto(new BigDecimal("21"));
    notaDebito.setMontoNoGravado(new BigDecimal("200"));
    notaDebito.setMotivo("Test alta nota debito - Cheque rechazado");
    notaDebito.setSubTotalBruto(new BigDecimal("100"));
    notaDebito.setTotal(new BigDecimal("321"));
    notaDebito.setTipoComprobante(TipoDeComprobante.NOTA_DEBITO_B);
    restTemplate.postForObject(
      apiPrefix + "/notas/debito/empresa/1/usuario/1/recibo/3?idProveedor=1&movimiento=COMPRA",
      notaDebito,
      NotaDebito.class);
  }

  private void crearNotaCreditoParaProveedor() {
    Long[] idsRenglonesFacura = new Long[]{3L};
    BigDecimal[] cantidades = new BigDecimal[]{new BigDecimal("5")};
    RenglonesDeFacturaParaNotaCreditoDTO renglonesDeFacturaParaNotaCreditoDTO =
      RenglonesDeFacturaParaNotaCreditoDTO.builder()
        .idsRenglonesFactura(idsRenglonesFacura)
        .cantidades(cantidades)
        .tipoDeComprobante(TipoDeComprobante.FACTURA_B)
        .build();
    List<RenglonNotaCredito> renglonesNotaCredito =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix
            + "/notas/renglon/credito/producto",
          renglonesDeFacturaParaNotaCreditoDTO,
          RenglonNotaCredito[].class));
    NotaCreditoDTO notaCreditoProveedor = NotaCreditoDTO.builder().build();
    notaCreditoProveedor.setRenglonesNotaCredito(renglonesNotaCredito);
    notaCreditoProveedor.setFecha(new Date());
    notaCreditoProveedor.setModificaStock(true);
    notaCreditoProveedor.setSubTotal(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/sub-total?importe="
          + renglonesNotaCredito.get(0).getImporteNeto(),
        BigDecimal.class));
    notaCreditoProveedor.setRecargoPorcentaje(BigDecimal.TEN);
    notaCreditoProveedor.setRecargoNeto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/recargo-neto?subTotal="
          + notaCreditoProveedor.getSubTotal()
          + "&recargoPorcentaje="
          + notaCreditoProveedor.getRecargoPorcentaje(),
        BigDecimal.class));
    notaCreditoProveedor.setDescuentoPorcentaje(new BigDecimal("25"));
    notaCreditoProveedor.setDescuentoNeto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/descuento-neto?subTotal="
          + notaCreditoProveedor.getSubTotal()
          + "&descuentoPorcentaje="
          + notaCreditoProveedor.getDescuentoPorcentaje(),
        BigDecimal.class));
    notaCreditoProveedor.setIva21Neto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/iva-neto?"
          + "tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&cantidades="
          + renglonesNotaCredito.get(0).getCantidad()
          + "&ivaPorcentajeRenglones="
          + renglonesNotaCredito.get(0).getIvaPorcentaje()
          + "&ivaNetoRenglones="
          + renglonesNotaCredito.get(0).getIvaNeto()
          + "&ivaPorcentaje=21"
          + "&descuentoPorcentaje=25"
          + "&recargoPorcentaje=10",
        BigDecimal.class));
    notaCreditoProveedor.setIva105Neto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/iva-neto?"
          + "tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&cantidades="
          + renglonesNotaCredito.get(0).getCantidad()
          + "&ivaPorcentajeRenglones="
          + renglonesNotaCredito.get(0).getIvaPorcentaje()
          + "&ivaNetoRenglones="
          + renglonesNotaCredito.get(0).getIvaNeto()
          + "&ivaPorcentaje=10.5"
          + "&descuentoPorcentaje=25"
          + "&recargoPorcentaje=10",
        BigDecimal.class));
    notaCreditoProveedor.setSubTotalBruto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/sub-total-bruto?"
          + "tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&subTotal="
          + notaCreditoProveedor.getSubTotal()
          + "&recargoNeto="
          + notaCreditoProveedor.getRecargoNeto()
          + "&descuentoNeto="
          + notaCreditoProveedor.getDescuentoNeto()
          + "&iva21Neto="
          + notaCreditoProveedor.getIva21Neto()
          + "&iva105Neto="
          + notaCreditoProveedor.getIva105Neto(),
        BigDecimal.class));
    notaCreditoProveedor.setTotal(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/total?subTotalBruto="
          + notaCreditoProveedor.getSubTotalBruto()
          + "&iva21Neto="
          + notaCreditoProveedor.getIva21Neto()
          + "&iva105Neto="
          + notaCreditoProveedor.getIva105Neto(),
        BigDecimal.class));
    notaCreditoProveedor.setMotivo("Devolución");
    restTemplate.postForObject(
      apiPrefix
        + "/notas/credito/empresa/1/usuario/1/factura/2?idProveedor=1&movimiento=COMPRA&modificarStock=true",
      notaCreditoProveedor,
      NotaCredito.class);
  }

  private void vincularClienteParaUsuarioInicial() {
    ClienteDTO cliente =
      ClienteDTO.builder()
        .bonificacion(BigDecimal.TEN)
        .nombreFiscal("Cliente test")
        .nombreFantasia("Cliente test.")
        .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
        .idFiscal(2355668L)
        .email("Cliente@test.com.br")
        .telefono("372461245")
        .contacto("El señor Oscuro")
        .idEmpresa(1L)
        .idCredencial(1L)
        .build();
    restTemplate.postForObject(
      apiPrefix + "/clientes",
      cliente,
      ClienteDTO.class);
  }

  private void abrirCaja() {
    restTemplate.postForObject(apiPrefix + "/cajas/apertura/empresas/1?saldoApertura=0", null, CajaDTO.class);
  }

  @BeforeEach
  void setup() {
    String md5Test = "098f6bcd4621d373cade4e832627b4f6";
    usuarioRepository.save(
      new UsuarioBuilder()
        .withUsername("test")
        .withPassword(md5Test)
        .withNombre("test")
        .withApellido("test")
        .withHabilitado(true)
        .build());
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
            String mensaje = IOUtils.toString(response.getBody());
            throw new RestClientResponseException(
              mensaje,
              response.getRawStatusCode(),
              response.getStatusText(),
              response.getHeaders(),
              null,
              Charset.defaultCharset());
          }
        });
    // set enviroment
    this.token =
      restTemplate
        .postForEntity(apiPrefix + "/login", new Credencial("test", "test"), String.class)
        .getBody();
    provinciaRepository.save(new ProvinciaBuilder().withNombre("Corrientes").build());
    provinciaRepository.save(new ProvinciaBuilder().withNombre("Misiones").build());
    provinciaRepository.save(new ProvinciaBuilder().withNombre("Chaco").build());
    localidadRepository.save(new LocalidadBuilder().withProvincia(provinciaRepository.findById(1L)).withNombre("Corrientes").withCodigoPostal("N3400").build());
    localidadRepository.save(new LocalidadBuilder().withProvincia(provinciaRepository.findById(1L)).withNombre("Mercedes").withCodigoPostal("W3470").build());
    localidadRepository.save(new LocalidadBuilder().withProvincia(provinciaRepository.findById(2L)).withNombre("Posadas").withCodigoPostal("N3300").build());
    localidadRepository.save(new LocalidadBuilder().withProvincia(provinciaRepository.findById(3L)).withNombre("Resistencia").withCodigoPostal("H3500").build());
    EmpresaDTO empresaDTO =
        EmpresaDTO.builder()
            .nombre("Globo Corporation")
            .lema("Enjoy the life")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(23154587589L)
            .ingresosBrutos(123456789L)
            .fechaInicioActividad(new Date(539924400000L))
            .email("support@globocorporation.com")
            .telefono("379 4895549")
            .ubicacion(UbicacionDTO.builder().calle("Napoles").numero(5600).idLocalidad(1L).build())
            .build();
    empresaDTO = restTemplate.postForObject(apiPrefix + "/empresas", empresaDTO, EmpresaDTO.class);
    FormaDePagoDTO formaDePago =
      FormaDePagoDTO.builder().afectaCaja(true).nombre("Efectivo").predeterminado(true).build();
    restTemplate.postForObject(
      apiPrefix + "/formas-de-pago?idEmpresa=" + empresaDTO.getId_Empresa(),
      formaDePago,
      FormaDePagoDTO.class);
    UsuarioDTO credencial =
        UsuarioDTO.builder()
            .username("marce")
            .password("marce123")
            .nombre("Marcelo")
            .apellido("Rockefeller")
            .email("marce.r@gmail.com")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .nombreFiscal("Peter Parker")
            .idFiscal(20362148952L)
            .telefono("379123452")
            .idEmpresa(empresaDTO.getId_Empresa())
            .idCredencial(credencial.getId_Usuario())
            .ubicacionFacturacion(
                UbicacionDTO.builder().calle("Rio Parana").numero(14500).idLocalidad(1L).build())
            .ubicacionEnvio(
                UbicacionDTO.builder().calle("Rio Uruguay").numero(15000).idLocalidad(1L).build())
            .build();
    restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    TransportistaDTO transportistaDTO =
        TransportistaDTO.builder()
            .nombre("Correo OCA")
            .web("pedidos@oca.com.ar")
            .telefono("379 5402356")
            .eliminado(false)
            .idEmpresa(1L)
            .ubicacion(
                UbicacionDTO.builder()
                    .calle("Rio Chico 15000")
                    .numero(4589)
                    .idLocalidad(1L)
                    .build())
            .build();
    restTemplate.postForObject(
        apiPrefix + "/transportistas?idEmpresa=1", transportistaDTO, TransportistaDTO.class);
    MedidaDTO medidaMetro = MedidaDTO.builder().nombre("Metro").build();
    MedidaDTO medidaKilo = MedidaDTO.builder().nombre("Kilo").build();
    restTemplate.postForObject(apiPrefix + "/medidas?idEmpresa=1", medidaMetro, MedidaDTO.class);
    restTemplate.postForObject(apiPrefix + "/medidas?idEmpresa=1", medidaKilo, MedidaDTO.class);
    ProveedorDTO proveedorDTO =
        ProveedorDTO.builder()
            .codigo("ABC123")
            .razonSocial("Chamaco S.R.L.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(23127895679L)
            .telPrimario("379 4356778")
            .telSecundario("379 4894514")
            .contacto("Raul Gamez")
            .email("chamacosrl@gmail.com")
            .web("www.chamacosrl.com.ar")
            .eliminado(false)
            .saldoCuentaCorriente(BigDecimal.ZERO)
            .ubicacion(
                UbicacionDTO.builder().calle("Av armenia").numero(45677).idLocalidad(1L).build())
            .idEmpresa(1L)
            .build();
    restTemplate.postForObject(apiPrefix + "/proveedores", proveedorDTO, Proveedor.class);
    RubroDTO rubro = RubroDTO.builder().nombre("Ferreteria").eliminado(false).build();
    restTemplate.postForObject(apiPrefix + "/rubros?idEmpresa=1", rubro, RubroDTO.class);
    this.vincularClienteParaUsuarioInicial();
  }

  @Test
  void shouldCrearFormaDePagoChequeQueAfectaCaja() {
    FormaDePagoDTO formaDePagoDTO =
      FormaDePagoDTO.builder().nombre("Cheque").afectaCaja(true).build();
    FormaDePagoDTO formaDePagoRecuperada =
      restTemplate.postForObject(
        apiPrefix + "/formas-de-pago?idEmpresa=1", formaDePagoDTO, FormaDePagoDTO.class);
    assertEquals(formaDePagoDTO, formaDePagoRecuperada);
  }

  @Test
  void shouldRegistrarNuevaCuentaComoResponsableInscripto() {
    RegistracionClienteAndUsuarioDTO registro =
      RegistracionClienteAndUsuarioDTO.builder()
        .apellido("Stark")
        .nombre("Sansa")
        .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
        .idEmpresa(1L)
        .email("sansa@got.com")
        .telefono("415789966")
        .password("caraDeMala")
        .recaptcha(recaptchaTestKey)
        .nombreFiscal("theRedWolf")
        .build();
    restTemplate.postForObject(apiPrefix + "/registracion", registro, Void.class);
    UsuarioDTO usuario = restTemplate.getForObject(apiPrefix + "/usuarios/3", UsuarioDTO.class);
    assertEquals(usuario.getNombre(), "Sansa");
    assertEquals(usuario.getApellido(), "Stark");
    ClienteDTO cliente = restTemplate.getForObject(apiPrefix + "/clientes/3", ClienteDTO.class);
    assertEquals(cliente.getNombreFiscal(), "theRedWolf");
  }

  @Test
  void shouldCrearEmpresaResponsableInscripto() {
    EmpresaDTO empresaNueva =
        EmpresaDTO.builder()
            .telefono("3795221144")
            .email("empresa@nueva.com")
            .fechaInicioActividad(new Date())
            .ingresosBrutos(21112244L)
            .idFiscal(7488521665766L)
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .lema("Hoy no se fía, mañana si.")
            .nombre("La gran idea")
            .ubicacion(
                UbicacionDTO.builder().idLocalidad(1L).calle("Rio Piacentin").numero(345).build())
            .build();
    EmpresaDTO empresaGuardada =
      restTemplate.postForObject(apiPrefix + "/empresas", empresaNueva, EmpresaDTO.class);
    assertEquals(empresaNueva, empresaGuardada);
  }

  @Test
  void shouldCrearEmpresaConUbicacion() {
    EmpresaDTO empresaNueva =
        EmpresaDTO.builder()
            .telefono("3795221144")
            .email("empresa@nueva.com")
            .fechaInicioActividad(new Date())
            .ingresosBrutos(21112244L)
            .idFiscal(7488521665766L)
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .lema("Hoy no se fía, mañana si.")
            .nombre("La gran idea")
            .ubicacion(
                UbicacionDTO.builder()
                    .calle("Valle Sagrado de los Enanos")
                    .numero(8877)
                    .idLocalidad(1L)
                    .build())
            .build();
    EmpresaDTO empresaGuardada =
        restTemplate.postForObject(apiPrefix + "/empresas", empresaNueva, EmpresaDTO.class);
    assertEquals(empresaNueva, empresaGuardada);
  }

  @Test
  void shouldEditarUbicacionDeEmpresa() {
    EmpresaDTO empresaNueva =
        EmpresaDTO.builder()
            .telefono("3795221144")
            .email("empresa@nueva.com")
            .fechaInicioActividad(new Date())
            .ingresosBrutos(21112244L)
            .idFiscal(7488521665766L)
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .lema("Hoy no se fía, mañana si.")
            .nombre("La gran idea")
            .ubicacion(
                UbicacionDTO.builder()
                    .calle("Valle Sagrado de los Enanos")
                    .numero(8877)
                    .idLocalidad(1L)
                    .build())
            .build();
    EmpresaDTO empresaGuardada =
        restTemplate.postForObject(apiPrefix + "/empresas", empresaNueva, EmpresaDTO.class);
    UbicacionDTO ubicacion = empresaGuardada.getUbicacion();
    ubicacion.setCalle("Los Rios Negros");
    ubicacion.setNumero(1456);
    empresaGuardada.setUbicacion(ubicacion);
    restTemplate.put(apiPrefix + "/empresas", empresaGuardada);
    empresaGuardada =
        restTemplate.getForObject(
            apiPrefix + "/empresas/" + empresaGuardada.getId_Empresa(), EmpresaDTO.class);
    assertEquals(ubicacion, empresaGuardada.getUbicacion());
  }

  @Test
  void shouldCrearClienteResponsableInscripto() {
    UsuarioDTO credencial =
        UsuarioDTO.builder()
            .username("elenanocañete")
            .password("siempredebarrio")
            .nombre("Juan")
            .apellido("Cañete")
            .email("caniete@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    assertEquals(cliente, clienteRecuperado);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    assertEquals(empresa.getNombre(), clienteRecuperado.getNombreEmpresa());
  }

  @Test
  void shouldCrearClienteConUbicaciones() {
    UbicacionDTO ubicacionDeFacturacion = UbicacionDTO.builder()
      .calle("Sarmiento")
      .numero(789)
      .idLocalidad(1L)
      .build();
    UbicacionDTO ubicacionDeEnvio = UbicacionDTO.builder()
      .calle("Belgrano")
      .numero(456)
      .idLocalidad(1L)
      .build();
    UsuarioDTO credencial =
      UsuarioDTO.builder()
        .username("elenanocañete")
        .password("siempredebarrio")
        .nombre("Juan")
        .apellido("Cañete")
        .email("caniete@yahoo.com.br")
        .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
        .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .ubicacionFacturacion(ubicacionDeFacturacion)
            .ubicacionEnvio(ubicacionDeEnvio)
            .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    assertEquals(cliente, clienteRecuperado);
    assertEquals(ubicacionDeFacturacion, clienteRecuperado.getUbicacionFacturacion());
  }

  @Test
  void shouldCrearClienteConUbicacionDeFacturacion() {
    UsuarioDTO credencial =
      UsuarioDTO.builder()
        .username("elenanocañete")
        .password("siempredebarrio")
        .nombre("Juan")
        .apellido("Cañete")
        .email("caniete@yahoo.com.br")
        .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
        .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    UbicacionDTO ubicacionDeFacturacion = UbicacionDTO.builder()
      .calle("Sarmiento")
      .numero(789)
      .idLocalidad(1L)
      .build();
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .ubicacionFacturacion(ubicacionDeFacturacion)
            .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    assertEquals(cliente, clienteRecuperado);
    assertEquals(ubicacionDeFacturacion, clienteRecuperado.getUbicacionFacturacion());
    assertNull(clienteRecuperado.getUbicacionEnvio());
  }

  @Test
  void shouldCrearClienteConUbicacionDeEnvio() {
    UsuarioDTO credencial =
      UsuarioDTO.builder()
        .username("elenanocañete")
        .password("siempredebarrio")
        .nombre("Juan")
        .apellido("Cañete")
        .email("caniete@yahoo.com.br")
        .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
        .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    UbicacionDTO ubicacionDeEnvio = UbicacionDTO.builder()
      .calle("Belgrano")
      .numero(456)
      .idLocalidad(1L)
      .build();
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .ubicacionEnvio(ubicacionDeEnvio)
            .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    assertEquals(cliente, clienteRecuperado);
    assertEquals(ubicacionDeEnvio, clienteRecuperado.getUbicacionEnvio());
    assertNull(clienteRecuperado.getUbicacionFacturacion());
  }

  @Test
  void shouldNotCrearClienteConUbicacionDeFacturacionSinLocalidad() {
    UsuarioDTO credencial =
        UsuarioDTO.builder()
            .username("elenanocañete")
            .password("siempredebarrio")
            .nombre("Juan")
            .apellido("Cañete")
            .email("caniete@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    UbicacionDTO ubicacionDeFacturacion =
      UbicacionDTO.builder().calle("Sarmiento").numero(789).build();
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .ubicacionFacturacion(ubicacionDeFacturacion)
            .build();
    try {
      restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    } catch (RestClientResponseException ex) {
      assertTrue(
          ex.getMessage()
              .startsWith("La ubicacion de facturacion debe poseer una localidad asociada."));
    }
  }

  @Test
  void shouldNotCrearClienteConUbicacionDeEnvioSinLocalidad() {
    UsuarioDTO credencial =
        UsuarioDTO.builder()
            .username("elenanocañete")
            .password("siempredebarrio")
            .nombre("Juan")
            .apellido("Cañete")
            .email("caniete@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    UbicacionDTO ubicacionDeEnvio = UbicacionDTO.builder().calle("Rosas").numero(6665).build();
    ClienteDTO cliente =
      ClienteDTO.builder()
        .bonificacion(BigDecimal.TEN)
        .nombreFiscal("Juan Fernando Cañete")
        .nombreFantasia("Menos mal que estamos nosotros.")
        .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
        .idFiscal(1244557L)
        .email("caniete@yahoo.com.br")
        .telefono("3785663322")
        .contacto("Ramon el hermano de Juan")
        .idEmpresa(1L)
        .idCredencial(credencial.getId_Usuario())
        .ubicacionEnvio(ubicacionDeEnvio)
        .build();
    try {
      restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    } catch (RestClientResponseException ex) {
      assertTrue(
          ex.getMessage().startsWith("La ubicacion de envio debe poseer una localidad asociada."));
    }
  }

  @Test
  void shouldCrearClienteYLuegoDarDeAltaUbicacionDeFacturacionModificandoCliente() {
    UsuarioDTO credencial =
        UsuarioDTO.builder()
            .username("elenanocañete")
            .password("siempredebarrio")
            .nombre("Juan")
            .apellido("Cañete")
            .email("caniete@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    clienteRecuperado.setUbicacionFacturacion(
        UbicacionDTO.builder().calle("14 de Julio").numero(785).idLocalidad(1L).build());
    restTemplate.put(apiPrefix + "/clientes", clienteRecuperado);
    clienteRecuperado = restTemplate.getForObject(apiPrefix + "/clientes/3", ClienteDTO.class);
    assertEquals("14 de Julio", clienteRecuperado.getUbicacionFacturacion().getCalle());
    assertEquals(785, clienteRecuperado.getUbicacionFacturacion().getNumero());
  }

  @Test
  void shouldCrearClienteYLuegoDarDeAltaUbicacionDeEnvioModificandoCliente() {
    UsuarioDTO credencial =
        UsuarioDTO.builder()
            .username("elenanocañete")
            .password("siempredebarrio")
            .nombre("Juan")
            .apellido("Cañete")
            .email("caniete@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    clienteRecuperado.setUbicacionEnvio(
        UbicacionDTO.builder().calle("8 de Agosto").numero(964).idLocalidad(1L).build());
    restTemplate.put(apiPrefix + "/clientes", clienteRecuperado);
    clienteRecuperado = restTemplate.getForObject(apiPrefix + "/clientes/3", ClienteDTO.class);
    assertEquals("8 de Agosto", clienteRecuperado.getUbicacionEnvio().getCalle());
    assertEquals(964, clienteRecuperado.getUbicacionEnvio().getNumero());
  }

  @Test
  void shouldCrearClienteYLuegoDarDeAltaUbicacionesModificandoCliente() {
    UsuarioDTO credencial =
      UsuarioDTO.builder()
        .username("elenanocañete")
        .password("siempredebarrio")
        .nombre("Juan")
        .apellido("Cañete")
        .email("caniete@yahoo.com.br")
        .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
        .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    ClienteDTO cliente =
      ClienteDTO.builder()
        .bonificacion(BigDecimal.TEN)
        .nombreFiscal("Juan Fernando Cañete")
        .nombreFantasia("Menos mal que estamos nosotros.")
        .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
        .idFiscal(1244557L)
        .email("caniete@yahoo.com.br")
        .telefono("3785663322")
        .contacto("Ramon el hermano de Juan")
        .idEmpresa(1L)
        .idCredencial(credencial.getId_Usuario())
        .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    clienteRecuperado.setUbicacionFacturacion(
        UbicacionDTO.builder().calle("Santa Cruz").numero(1247).idLocalidad(1L).build());
    clienteRecuperado.setUbicacionEnvio(
        UbicacionDTO.builder().calle("8 de Agosto").numero(964).idLocalidad(1L).build());
    restTemplate.put(apiPrefix + "/clientes", clienteRecuperado);
    clienteRecuperado = restTemplate.getForObject(apiPrefix + "/clientes/3", ClienteDTO.class);
    assertEquals("Santa Cruz", clienteRecuperado.getUbicacionFacturacion().getCalle());
    assertEquals(1247, clienteRecuperado.getUbicacionFacturacion().getNumero());
    assertEquals("8 de Agosto", clienteRecuperado.getUbicacionEnvio().getCalle());
    assertEquals(964, clienteRecuperado.getUbicacionEnvio().getNumero());
  }

  @Test
  void shouldModificarUbicacionesDeClientes() {
    UsuarioDTO credencial =
        UsuarioDTO.builder()
            .username("elenanocañete")
            .password("siempredebarrio")
            .nombre("Juan")
            .apellido("Cañete")
            .email("caniete@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .ubicacionFacturacion(
                UbicacionDTO.builder().calle("Sarmiento").numero(789).idLocalidad(1L).build())
            .ubicacionEnvio(
                UbicacionDTO.builder().calle("Belgrano").numero(456).idLocalidad(1L).build())
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    UbicacionDTO ubicacionFacturacion = clienteRecuperado.getUbicacionFacturacion();
    ubicacionFacturacion.setCalle("Calle Nueva");
    ubicacionFacturacion.setNumero(8779);
    UbicacionDTO ubicacionEnvio = clienteRecuperado.getUbicacionEnvio();
    ubicacionEnvio.setCalle("Segunda Calle");
    ubicacionEnvio.setNumero(4550);
    clienteRecuperado.setUbicacionFacturacion(ubicacionFacturacion);
    clienteRecuperado.setUbicacionEnvio(ubicacionEnvio);
    restTemplate.put(apiPrefix + "/clientes", clienteRecuperado);
    clienteRecuperado = restTemplate.getForObject(apiPrefix + "/clientes/3", ClienteDTO.class);
    assertEquals(ubicacionFacturacion, clienteRecuperado.getUbicacionFacturacion());
    assertEquals(ubicacionEnvio, clienteRecuperado.getUbicacionEnvio());
  }

  @Test
  void shouldNotEliminarUbicacionesDeCliente() {
    UsuarioDTO credencial =
        UsuarioDTO.builder()
            .username("elenanocañete")
            .password("siempredebarrio")
            .nombre("Juan")
            .apellido("Cañete")
            .email("caniete@yahoo.com.br")
            .roles(new ArrayList<>(Collections.singletonList(Rol.COMPRADOR)))
            .build();
    credencial = restTemplate.postForObject(apiPrefix + "/usuarios", credencial, UsuarioDTO.class);
    ClienteDTO cliente =
        ClienteDTO.builder()
            .bonificacion(BigDecimal.TEN)
            .nombreFiscal("Juan Fernando Cañete")
            .nombreFantasia("Menos mal que estamos nosotros.")
            .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
            .idFiscal(1244557L)
            .email("caniete@yahoo.com.br")
            .telefono("3785663322")
            .contacto("Ramon el hermano de Juan")
            .ubicacionFacturacion(
                UbicacionDTO.builder().calle("Sarmiento").numero(789).idLocalidad(1L).build())
            .ubicacionEnvio(
                UbicacionDTO.builder().calle("Belgrano").numero(456).idLocalidad(1L).build())
            .idEmpresa(1L)
            .idCredencial(credencial.getId_Usuario())
            .build();
    ClienteDTO clienteRecuperado =
        restTemplate.postForObject(apiPrefix + "/clientes", cliente, ClienteDTO.class);
    UbicacionDTO ubicacionFacturacion = clienteRecuperado.getUbicacionFacturacion();
    UbicacionDTO ubicacionEnvio = clienteRecuperado.getUbicacionEnvio();
    clienteRecuperado.setUbicacionFacturacion(null);
    clienteRecuperado.setUbicacionEnvio(null);
    restTemplate.put(apiPrefix + "/clientes", clienteRecuperado);
    clienteRecuperado = restTemplate.getForObject(apiPrefix + "/clientes/3", ClienteDTO.class);
    assertEquals(ubicacionFacturacion, clienteRecuperado.getUbicacionFacturacion());
    assertEquals(ubicacionEnvio, clienteRecuperado.getUbicacionEnvio());
  }

  @Test
  void shouldCrearProveedorConUbicacion() {
    UbicacionDTO ubicacion = UbicacionDTO.builder()
      .calle("Belgrano")
      .numero(456)
      .idLocalidad(1L)
      .build();
    ProveedorDTO proveedor =
      ProveedorDTO.builder()
        .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
        .codigo("555888")
        .contacto("Ricardo")
        .email("ricardodelbarrio@gmail.com")
        .telPrimario("4512778851")
        .telSecundario("784551122")
        .web("")
        .razonSocial("Migral Compuesto")
        .ubicacion(ubicacion)
        .idEmpresa(1L)
        .build();
    ProveedorDTO proveedorRecuperado =
      restTemplate.postForObject(
        apiPrefix + "/proveedores", proveedor, ProveedorDTO.class);
    assertEquals(proveedor, proveedorRecuperado);
  }

  @Test
  void shouldCrearProveedorConUbicacionYEditarla() {
    UbicacionDTO ubicacion = UbicacionDTO.builder()
      .calle("Belgrano")
      .numero(456)
      .idLocalidad(1L)
      .build();
    ProveedorDTO proveedor =
      ProveedorDTO.builder()
        .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
        .codigo("555888")
        .contacto("Ricardo")
        .email("ricardodelbarrio@gmail.com")
        .telPrimario("4512778851")
        .telSecundario("784551122")
        .web("")
        .razonSocial("Migral Compuesto")
        .ubicacion(ubicacion)
        .idEmpresa(1L)
        .build();
    ProveedorDTO proveedorRecuperado =
      restTemplate.postForObject(
        apiPrefix + "/proveedores", proveedor, ProveedorDTO.class);
    ubicacion = proveedorRecuperado.getUbicacion();
    ubicacion.setCalle("Rosas");
    ubicacion.setNumero(9666);
    proveedorRecuperado.setUbicacion(ubicacion);
    restTemplate.put(apiPrefix + "/proveedores", proveedorRecuperado);
    proveedorRecuperado = restTemplate.getForObject(apiPrefix+ "/proveedores/" + proveedorRecuperado.getId_Proveedor(), ProveedorDTO.class);
    assertEquals(ubicacion, proveedorRecuperado.getUbicacion());
  }

  @Test
  void shouldCrearTransportista() {
    TransportistaDTO transportista =
        TransportistaDTO.builder()
            .telefono("78946551122")
            .web("Ronollega.com")
            .nombre("Transporte Segu Ronollega")
            .idEmpresa(1L)
            .build();
    TransportistaDTO transportistaRecuperado =
        restTemplate.postForObject(
            apiPrefix + "/transportistas", transportista, TransportistaDTO.class);
    assertEquals(transportista, transportistaRecuperado);
  }

  @Test
  void shouldCrearTransportistaConUbicacion() {
    TransportistaDTO transportista =
        TransportistaDTO.builder()
            .telefono("78946551122")
            .web("Ronollega.com")
            .nombre("Transporte Segu Ronollega")
            .idEmpresa(1L)
            .ubicacion(
                UbicacionDTO.builder()
                    .calle("Los Rios Puros Nacidos del Mar de la Calma")
                    .numero(784445)
                    .idLocalidad(1L)
                    .build())
            .build();
    TransportistaDTO transportistaRecuperado =
        restTemplate.postForObject(
            apiPrefix + "/transportistas", transportista, TransportistaDTO.class);
    assertEquals(transportista, transportistaRecuperado);
  }

  @Test
  void shouldEditarUbicacionDeTransportista() {
    TransportistaDTO transportista =
        TransportistaDTO.builder()
            .telefono("78946551122")
            .web("Ronollega.com")
            .nombre("Transporte Segu Ronollega")
            .idEmpresa(1L)
            .ubicacion(
                UbicacionDTO.builder()
                    .calle("Los Rios Puros Nacidos del Mar de la Calma")
                    .numero(784445)
                    .idLocalidad(1L)
                    .build())
            .build();
    TransportistaDTO transportistaRecuperado =
        restTemplate.postForObject(
            apiPrefix + "/transportistas", transportista, TransportistaDTO.class);
    UbicacionDTO ubicacion = transportistaRecuperado.getUbicacion();
    ubicacion.setCalle("Los Prados Rosas de los Elfos Sagrados");
    ubicacion.setNumero(789996);
    transportistaRecuperado.setUbicacion(ubicacion);
    restTemplate.put(apiPrefix + "/transportistas", transportistaRecuperado);
    transportistaRecuperado =
        restTemplate.getForObject(
            apiPrefix + "/transportistas/" + transportistaRecuperado.getId_Transportista(),
            TransportistaDTO.class);
    assertEquals(ubicacion, transportistaRecuperado.getUbicacion());
  }

  @Test
  void shouldEliminarUbicacionesDelClienteAlEliminarlo() {
    this.shouldCrearClienteConUbicaciones();
    restTemplate.delete(apiPrefix + "/clientes/3");
    assertNull(restTemplate.getForObject(apiPrefix + "/ubicaciones/7", UbicacionDTO.class));
    assertNull(restTemplate.getForObject(apiPrefix + "/ubicaciones/6", UbicacionDTO.class));
  }

  @Test
  void shouldEliminarUbicacionesDelEmpresaAlEliminarla() {
    this.shouldCrearEmpresaResponsableInscripto();
    restTemplate.delete(apiPrefix + "/empresas/2");
    assertNull(restTemplate.getForObject(apiPrefix + "/ubicaciones/6", UbicacionDTO.class));
  }

  @Test
  void shouldEliminarUbicacionesDelProveedorAlEliminarlo() {
    this.shouldCrearProveedorConUbicacion();
    restTemplate.delete(apiPrefix + "/proveedores/2");
    assertNull(restTemplate.getForObject(apiPrefix + "/ubicaciones/6", UbicacionDTO.class));
  }

  @Test
  void shouldEliminarUbicacionesDelTransportistaAlEliminarla() {
    this.shouldCrearTransportistaConUbicacion();
    restTemplate.delete(apiPrefix + "/transportistas/2");
    assertNull(restTemplate.getForObject(apiPrefix + "/ubicaciones/6", UbicacionDTO.class));
  }

  @Test
  void shouldCrearMedida() {
    MedidaDTO medida = MedidaDTO.builder().nombre("Longitud de Plank").build();
    MedidaDTO medidaRecuperada =
      restTemplate.postForObject(apiPrefix + "/medidas?idEmpresa=1", medida, MedidaDTO.class);
    assertEquals(medida, medidaRecuperada);
  }

  @Test
  void shouldCrearRubro() {
    RubroDTO rubro = RubroDTO.builder().nombre("Reparación de Ovnis").build();
    RubroDTO rubroRecuperado =
      restTemplate.postForObject(apiPrefix + "/rubros?idEmpresa=1", rubro, RubroDTO.class);
    assertEquals(rubro, rubroRecuperado);
  }

  @Test
  void shouldCrearProveedorResponsableInscripto() {
    ProveedorDTO proveedor =
      ProveedorDTO.builder()
        .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
        .codigo("555888")
        .contacto("Ricardo")
        .email("ricardodelbarrio@gmail.com")
        .telPrimario("4512778851")
        .telSecundario("784551122")
        .web("")
        .razonSocial("Migral Compuesto")
        .idEmpresa(1L)
        .build();
    ProveedorDTO proveedorRecuperado =
      restTemplate.postForObject(
        apiPrefix + "/proveedores", proveedor, ProveedorDTO.class);
    assertEquals(proveedor, proveedorRecuperado);
  }

  @Test
  void shouldCrearFacturaVentaA() {
    this.crearProductos();
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=6"
          + "&descuentoPorcentaje=10",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=3"
          + "&descuentoPorcentaje=5",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto = subTotal.add(recargo_neto).subtract(descuento_neto);
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/1", Cliente.class);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    FacturaVentaDTO facturaVentaA =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaA.setObservaciones("Factura Venta A test");
    facturaVentaA.setTipoComprobante(TipoDeComprobante.FACTURA_A);
    facturaVentaA.setRenglones(renglones);
    facturaVentaA.setSubTotal(subTotal);
    facturaVentaA.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaA.setRecargoNeto(recargo_neto);
    facturaVentaA.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaA.setDescuentoNeto(descuento_neto);
    facturaVentaA.setSubTotalBruto(subTotalBruto);
    facturaVentaA.setIva105Neto(iva_105_netoFactura);
    facturaVentaA.setIva21Neto(iva_21_netoFactura);
    facturaVentaA.setTotal(total);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    FacturaVentaDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/venta?"
          + "idCliente="
          + cliente.getId_Cliente()
          + "&idEmpresa="
          + empresa.getId_Empresa()
          + "&idUsuario="
          + credencial.getId_Usuario()
          + "&idTransportista="
          + transportista.getId_Transportista(),
        facturaVentaA,
        FacturaVentaDTO[].class);
    assertEquals(facturaVentaA, facturas[0]);
    assertEquals(cliente.getNombreFiscal(), facturas[0].getNombreFiscalCliente());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      credencial.getNombre()
        + " "
        + credencial.getApellido()
        + " ("
        + credencial.getUsername()
        + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldEmitirReporteFactura() {
    this.shouldCrearFacturaVentaA();
    restTemplate.getForObject(apiPrefix + "/facturas/1/reporte", byte[].class);
  }

  @Test
  void shouldCrearFacturaVentaB() {
    this.crearProductos();
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=5"
          + "&descuentoPorcentaje=20",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=2"
          + "&descuentoPorcentaje=0",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto =
      subTotal
        .add(recargo_neto)
        .subtract(descuento_neto)
        .subtract(iva_105_netoFactura.add(iva_21_netoFactura));
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/1", Cliente.class);
    FacturaVentaDTO facturaVentaB =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaB.setObservaciones("Factura Venta B test");
    facturaVentaB.setTipoComprobante(TipoDeComprobante.FACTURA_B);
    facturaVentaB.setRenglones(renglones);
    facturaVentaB.setSubTotal(subTotal);
    facturaVentaB.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaB.setRecargoNeto(recargo_neto);
    facturaVentaB.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaB.setDescuentoNeto(descuento_neto);
    facturaVentaB.setSubTotalBruto(subTotalBruto);
    facturaVentaB.setIva105Neto(iva_105_netoFactura);
    facturaVentaB.setIva21Neto(iva_21_netoFactura);
    facturaVentaB.setTotal(total);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    FacturaVentaDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/venta?"
          + "idCliente="
          + cliente.getId_Cliente()
          + "&idEmpresa="
          + empresa.getId_Empresa()
          + "&idUsuario="
          + credencial.getId_Usuario()
          + "&idTransportista="
          + transportista.getId_Transportista(),
        facturaVentaB,
        FacturaVentaDTO[].class);
    assertEquals(facturaVentaB, facturas[0]);
    assertEquals(cliente.getNombreFiscal(), facturas[0].getNombreFiscalCliente());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      credencial.getNombre()
        + " "
        + credencial.getApellido()
        + " ("
        + credencial.getUsername()
        + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaVentaC() {
    this.crearProductos();
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_C
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=5"
          + "&descuentoPorcentaje=20",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_C
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=2"
          + "&descuentoPorcentaje=0",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto =
      subTotal
        .add(recargo_neto)
        .subtract(descuento_neto)
        .subtract(iva_105_netoFactura.add(iva_21_netoFactura));
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/1", Cliente.class);
    FacturaVentaDTO facturaVentaC =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaC.setObservaciones("Factura Venta C test");
    facturaVentaC.setTipoComprobante(TipoDeComprobante.FACTURA_C);
    facturaVentaC.setRenglones(renglones);
    facturaVentaC.setSubTotal(subTotal);
    facturaVentaC.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaC.setRecargoNeto(recargo_neto);
    facturaVentaC.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaC.setDescuentoNeto(descuento_neto);
    facturaVentaC.setSubTotalBruto(subTotalBruto);
    facturaVentaC.setIva105Neto(iva_105_netoFactura);
    facturaVentaC.setIva21Neto(iva_21_netoFactura);
    facturaVentaC.setTotal(total);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    FacturaVentaDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/venta?"
          + "idCliente="
          + cliente.getId_Cliente()
          + "&idEmpresa="
          + empresa.getId_Empresa()
          + "&idUsuario="
          + credencial.getId_Usuario()
          + "&idTransportista="
          + transportista.getId_Transportista(),
        facturaVentaC,
        FacturaVentaDTO[].class);
    assertEquals(facturaVentaC, facturas[0]);
    assertEquals(cliente.getNombreFiscal(), facturas[0].getNombreFiscalCliente());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      credencial.getNombre()
        + " "
        + credencial.getApellido()
        + " ("
        + credencial.getUsername()
        + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaVentaX() {
    this.crearProductos();
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_X
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=6"
          + "&descuentoPorcentaje=10",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_X
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=3"
          + "&descuentoPorcentaje=5",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal subTotalBruto = subTotal.add(recargo_neto).subtract(descuento_neto);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/1", Cliente.class);
    FacturaVentaDTO facturaVentaX =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaX.setObservaciones("Factura Venta X test");
    facturaVentaX.setTipoComprobante(TipoDeComprobante.FACTURA_X);
    facturaVentaX.setRenglones(renglones);
    facturaVentaX.setSubTotal(subTotal);
    facturaVentaX.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaX.setRecargoNeto(recargo_neto);
    facturaVentaX.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaX.setDescuentoNeto(descuento_neto);
    facturaVentaX.setSubTotalBruto(subTotalBruto);
    facturaVentaX.setIva105Neto(BigDecimal.ZERO);
    facturaVentaX.setIva21Neto(BigDecimal.ZERO);
    facturaVentaX.setTotal(subTotalBruto);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    FacturaVentaDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/venta?"
          + "idCliente="
          + cliente.getId_Cliente()
          + "&idEmpresa="
          + empresa.getId_Empresa()
          + "&idUsuario="
          + credencial.getId_Usuario()
          + "&idTransportista="
          + transportista.getId_Transportista(),
        facturaVentaX,
        FacturaVentaDTO[].class);
    assertEquals(facturaVentaX, facturas[0]);
    assertEquals(cliente.getNombreFiscal(), facturas[0].getNombreFiscalCliente());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      credencial.getNombre()
        + " "
        + credencial.getApellido()
        + " ("
        + credencial.getUsername()
        + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaVentaY() {
    this.crearProductos();
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_Y
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=6"
          + "&descuentoPorcentaje=10",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_Y
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=3"
          + "&descuentoPorcentaje=5",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto =
      subTotal
        .add(recargo_neto)
        .subtract(descuento_neto)
        .subtract(iva_105_netoFactura.add(iva_21_netoFactura));
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/1", Cliente.class);
    FacturaVentaDTO facturaVentaY =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaY.setObservaciones("Factura Venta Y test");
    facturaVentaY.setTipoComprobante(TipoDeComprobante.FACTURA_Y);
    facturaVentaY.setRenglones(renglones);
    facturaVentaY.setSubTotal(subTotal);
    facturaVentaY.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaY.setRecargoNeto(recargo_neto);
    facturaVentaY.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaY.setDescuentoNeto(descuento_neto);
    facturaVentaY.setSubTotalBruto(subTotalBruto);
    facturaVentaY.setIva105Neto(iva_105_netoFactura);
    facturaVentaY.setIva21Neto(iva_21_netoFactura);
    facturaVentaY.setTotal(total);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    FacturaVentaDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/venta?"
          + "idCliente="
          + cliente.getId_Cliente()
          + "&idEmpresa="
          + empresa.getId_Empresa()
          + "&idUsuario="
          + credencial.getId_Usuario()
          + "&idTransportista="
          + transportista.getId_Transportista(),
        facturaVentaY,
        FacturaVentaDTO[].class);
    assertEquals(facturaVentaY, facturas[0]);
    assertEquals(cliente.getNombreFiscal(), facturas[0].getNombreFiscalCliente());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      credencial.getNombre()
        + " "
        + credencial.getApellido()
        + " ("
        + credencial.getUsername()
        + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaVentaPresupuesto() {
    this.crearProductos();
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.PRESUPUESTO
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=5"
          + "&descuentoPorcentaje=20",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.PRESUPUESTO
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=2"
          + "&descuentoPorcentaje=0",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto =
      subTotal
        .add(recargo_neto)
        .subtract(descuento_neto)
        .subtract(iva_105_netoFactura.add(iva_21_netoFactura));
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/1", Cliente.class);
    FacturaVentaDTO facturaVentaPresupuesto =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaPresupuesto.setObservaciones("Factura Venta Presupuesto test");
    facturaVentaPresupuesto.setTipoComprobante(TipoDeComprobante.PRESUPUESTO);
    facturaVentaPresupuesto.setRenglones(renglones);
    facturaVentaPresupuesto.setSubTotal(subTotal);
    facturaVentaPresupuesto.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaPresupuesto.setRecargoNeto(recargo_neto);
    facturaVentaPresupuesto.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaPresupuesto.setDescuentoNeto(descuento_neto);
    facturaVentaPresupuesto.setSubTotalBruto(subTotalBruto);
    facturaVentaPresupuesto.setIva105Neto(iva_105_netoFactura);
    facturaVentaPresupuesto.setIva21Neto(iva_21_netoFactura);
    facturaVentaPresupuesto.setTotal(total);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    FacturaVentaDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/venta?"
          + "idCliente="
          + cliente.getId_Cliente()
          + "&idEmpresa="
          + empresa.getId_Empresa()
          + "&idUsuario="
          + credencial.getId_Usuario()
          + "&idTransportista="
          + transportista.getId_Transportista(),
        facturaVentaPresupuesto,
        FacturaVentaDTO[].class);
    assertEquals(facturaVentaPresupuesto, facturas[0]);
    assertEquals(cliente.getNombreFiscal(), facturas[0].getNombreFiscalCliente());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      credencial.getNombre()
        + " "
        + credencial.getApellido()
        + " ("
        + credencial.getUsername()
        + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaVentaXParaClienteSinUbicacionDeFacturacion() {
    this.shouldCrearClienteResponsableInscripto();
    this.crearProductos();
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_X
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=6"
          + "&descuentoPorcentaje=10",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_X
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=3"
          + "&descuentoPorcentaje=5",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal subTotalBruto = subTotal.add(recargo_neto).subtract(descuento_neto);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/2", Cliente.class);
    FacturaVentaDTO facturaVentaX =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaX.setObservaciones("Factura Venta X test");
    facturaVentaX.setTipoComprobante(TipoDeComprobante.FACTURA_X);
    facturaVentaX.setRenglones(renglones);
    facturaVentaX.setSubTotal(subTotal);
    facturaVentaX.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaX.setRecargoNeto(recargo_neto);
    facturaVentaX.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaX.setDescuentoNeto(descuento_neto);
    facturaVentaX.setSubTotalBruto(subTotalBruto);
    facturaVentaX.setIva105Neto(BigDecimal.ZERO);
    facturaVentaX.setIva21Neto(BigDecimal.ZERO);
    facturaVentaX.setTotal(subTotalBruto);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    FacturaVentaDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/venta?"
          + "idCliente="
          + cliente.getId_Cliente()
          + "&idEmpresa="
          + empresa.getId_Empresa()
          + "&idUsuario="
          + credencial.getId_Usuario()
          + "&idTransportista="
          + transportista.getId_Transportista(),
        facturaVentaX,
        FacturaVentaDTO[].class);
    assertEquals(facturaVentaX, facturas[0]);
    assertEquals(cliente.getNombreFiscal(), facturas[0].getNombreFiscalCliente());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      credencial.getNombre()
        + " "
        + credencial.getApellido()
        + " ("
        + credencial.getUsername()
        + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldNotCrearFacturaVentaAParaClienteSinUbicacionDeFacturacion() {
    this.shouldCrearClienteResponsableInscripto();
    this.crearProductos();
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=6"
          + "&descuentoPorcentaje=10",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=3"
          + "&descuentoPorcentaje=5",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto = subTotal.add(recargo_neto).subtract(descuento_neto);
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/2", Cliente.class);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    FacturaVentaDTO facturaVentaA =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaA.setObservaciones("Factura Venta A test");
    facturaVentaA.setTipoComprobante(TipoDeComprobante.FACTURA_A);
    facturaVentaA.setRenglones(renglones);
    facturaVentaA.setSubTotal(subTotal);
    facturaVentaA.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaA.setRecargoNeto(recargo_neto);
    facturaVentaA.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaA.setDescuentoNeto(descuento_neto);
    facturaVentaA.setSubTotalBruto(subTotalBruto);
    facturaVentaA.setIva105Neto(iva_105_netoFactura);
    facturaVentaA.setIva21Neto(iva_21_netoFactura);
    facturaVentaA.setTotal(total);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    try {
      restTemplate.postForObject(
          apiPrefix
              + "/facturas/venta?"
              + "idCliente="
              + cliente.getId_Cliente()
              + "&idEmpresa="
              + empresa.getId_Empresa()
              + "&idUsuario="
              + credencial.getId_Usuario()
              + "&idTransportista="
              + transportista.getId_Transportista(),
          facturaVentaA,
          FacturaVentaDTO[].class);
    } catch (RestClientResponseException ex) {
      assertTrue(
          ex.getMessage().startsWith("La ubicacion de facturacion se encuentra vacia."));
    }
  }

  @Test
  void shouldCrearFacturaCompraA() {
    this.crearProductos();
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=1"
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=4"
          + "&descuentoPorcentaje=20",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=2"
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=3"
          + "&descuentoPorcentaje=0",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = BigDecimal.TEN;
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto = subTotal.add(recargo_neto).subtract(descuento_neto);
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    ProveedorDTO proveedor =
      restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    FacturaCompraDTO facturaCompraA = FacturaCompraDTO.builder().build();
    facturaCompraA.setObservaciones("Factura Compra A test");
    facturaCompraA.setRazonSocialProveedor(proveedor.getRazonSocial());
    facturaCompraA.setFecha(new Date());
    facturaCompraA.setTipoComprobante(TipoDeComprobante.FACTURA_A);
    facturaCompraA.setRenglones(renglones);
    facturaCompraA.setSubTotal(subTotal);
    facturaCompraA.setRecargoPorcentaje(recargoPorcentaje);
    facturaCompraA.setRecargoNeto(recargo_neto);
    facturaCompraA.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaCompraA.setDescuentoNeto(descuento_neto);
    facturaCompraA.setSubTotalBruto(subTotalBruto);
    facturaCompraA.setIva105Neto(iva_105_netoFactura);
    facturaCompraA.setIva21Neto(iva_21_netoFactura);
    facturaCompraA.setTotal(total);
    FacturaCompraDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/compra?"
          + "idProveedor=1&idEmpresa=1&idUsuario=2&idTransportista=1",
        facturaCompraA,
        FacturaCompraDTO[].class);
    assertEquals(facturaCompraA, facturas[0]);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    UsuarioDTO usuario = restTemplate.getForObject(apiPrefix + "/usuarios/2", UsuarioDTO.class);
    TransportistaDTO transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", TransportistaDTO.class);
    assertEquals(proveedor.getRazonSocial(), facturas[0].getRazonSocialProveedor());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      usuario.getNombre() + " " + usuario.getApellido() + " (" + usuario.getUsername() + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaCompraB() {
    this.crearProductos();
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=1"
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=5"
          + "&descuentoPorcentaje=20",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=2"
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=2"
          + "&descuentoPorcentaje=0",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = BigDecimal.TEN;
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto =
      subTotal
        .add(recargo_neto)
        .subtract(descuento_neto)
        .subtract(iva_105_netoFactura.add(iva_21_netoFactura));
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    FacturaCompraDTO facturaCompraB = FacturaCompraDTO.builder().build();
    facturaCompraB.setObservaciones("Factura Compra B test");
    facturaCompraB.setFecha(new Date());
    facturaCompraB.setTipoComprobante(TipoDeComprobante.FACTURA_B);
    facturaCompraB.setRenglones(renglones);
    facturaCompraB.setSubTotal(subTotal);
    facturaCompraB.setRecargoPorcentaje(recargoPorcentaje);
    facturaCompraB.setRecargoNeto(recargo_neto);
    facturaCompraB.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaCompraB.setDescuentoNeto(descuento_neto);
    facturaCompraB.setSubTotalBruto(subTotalBruto);
    facturaCompraB.setIva105Neto(iva_105_netoFactura);
    facturaCompraB.setIva21Neto(iva_21_netoFactura);
    facturaCompraB.setTotal(total);
    FacturaCompraDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/compra?"
          + "idProveedor=1&idEmpresa=1&idUsuario=2&idTransportista=1",
        facturaCompraB,
        FacturaCompraDTO[].class);
    facturaCompraB.setRazonSocialProveedor("Chamaco S.R.L.");
    assertEquals(facturaCompraB, facturas[0]);
    ProveedorDTO proveedor =
      restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    UsuarioDTO usuario = restTemplate.getForObject(apiPrefix + "/usuarios/2", UsuarioDTO.class);
    TransportistaDTO transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", TransportistaDTO.class);
    assertEquals(proveedor.getRazonSocial(), facturas[0].getRazonSocialProveedor());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      usuario.getNombre() + " " + usuario.getApellido() + " (" + usuario.getUsername() + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaCompraC() {
    this.crearProductos();
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=1"
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_C
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=3"
          + "&descuentoPorcentaje=20",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=2"
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_C
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=1"
          + "&descuentoPorcentaje=0",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = BigDecimal.TEN;
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto =
      subTotal
        .add(recargo_neto)
        .subtract(descuento_neto)
        .subtract(iva_105_netoFactura.add(iva_21_netoFactura));
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    FacturaCompraDTO facturaCompraC = FacturaCompraDTO.builder().build();
    facturaCompraC.setObservaciones("Factura Compra C test");
    facturaCompraC.setFecha(new Date());
    facturaCompraC.setTipoComprobante(TipoDeComprobante.FACTURA_C);
    facturaCompraC.setRenglones(renglones);
    facturaCompraC.setSubTotal(subTotal);
    facturaCompraC.setRecargoPorcentaje(recargoPorcentaje);
    facturaCompraC.setRecargoNeto(recargo_neto);
    facturaCompraC.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaCompraC.setDescuentoNeto(descuento_neto);
    facturaCompraC.setSubTotalBruto(subTotalBruto);
    facturaCompraC.setIva105Neto(iva_105_netoFactura);
    facturaCompraC.setIva21Neto(iva_21_netoFactura);
    facturaCompraC.setTotal(total);
    FacturaCompraDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/compra?"
          + "idProveedor=1&idEmpresa=1&idUsuario=2&idTransportista=1",
        facturaCompraC,
        FacturaCompraDTO[].class);
    facturaCompraC.setRazonSocialProveedor("Chamaco S.R.L.");
    assertEquals(facturaCompraC, facturas[0]);
    ProveedorDTO proveedor =
      restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    UsuarioDTO usuario = restTemplate.getForObject(apiPrefix + "/usuarios/2", UsuarioDTO.class);
    TransportistaDTO transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", TransportistaDTO.class);
    assertEquals(proveedor.getRazonSocial(), facturas[0].getRazonSocialProveedor());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      usuario.getNombre() + " " + usuario.getApellido() + " (" + usuario.getUsername() + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaCompraX() {
    this.crearProductos();
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=1"
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_X
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=5"
          + "&descuentoPorcentaje=20",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=2"
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_X
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=2"
          + "&descuentoPorcentaje=0",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = BigDecimal.TEN;
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto =
      subTotal
        .add(recargo_neto)
        .subtract(descuento_neto)
        .subtract(iva_105_netoFactura.add(iva_21_netoFactura));
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    FacturaCompraDTO facturaCompraX = FacturaCompraDTO.builder().build();
    facturaCompraX.setObservaciones("Factura Compra X test");
    facturaCompraX.setFecha(new Date());
    facturaCompraX.setTipoComprobante(TipoDeComprobante.FACTURA_X);
    facturaCompraX.setRenglones(renglones);
    facturaCompraX.setSubTotal(subTotal);
    facturaCompraX.setRecargoPorcentaje(recargoPorcentaje);
    facturaCompraX.setRecargoNeto(recargo_neto);
    facturaCompraX.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaCompraX.setDescuentoNeto(descuento_neto);
    facturaCompraX.setSubTotalBruto(subTotalBruto);
    facturaCompraX.setIva105Neto(BigDecimal.ZERO);
    facturaCompraX.setIva21Neto(BigDecimal.ZERO);
    facturaCompraX.setTotal(total);
    FacturaCompraDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/compra?"
          + "idProveedor=1&idEmpresa=1&idUsuario=2&idTransportista=1",
        facturaCompraX,
        FacturaCompraDTO[].class);
    facturaCompraX.setRazonSocialProveedor("Chamaco S.R.L.");
    assertEquals(facturaCompraX, facturas[0]);
    ProveedorDTO proveedor =
      restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    UsuarioDTO usuario = restTemplate.getForObject(apiPrefix + "/usuarios/2", UsuarioDTO.class);
    TransportistaDTO transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", TransportistaDTO.class);
    assertEquals(proveedor.getRazonSocial(), facturas[0].getRazonSocialProveedor());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      usuario.getNombre() + " " + usuario.getApellido() + " (" + usuario.getUsername() + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCrearFacturaCompraPresupuesto() {
    this.crearProductos();
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=1"
          + "&tipoDeComprobante="
          + TipoDeComprobante.PRESUPUESTO
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=5"
          + "&descuentoPorcentaje=20",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto=2"
          + "&tipoDeComprobante="
          + TipoDeComprobante.PRESUPUESTO
          + "&movimiento="
          + Movimiento.COMPRA
          + "&cantidad=2"
          + "&descuentoPorcentaje=0",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = BigDecimal.TEN;
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto =
      subTotal
        .add(recargo_neto)
        .subtract(descuento_neto)
        .subtract(iva_105_netoFactura.add(iva_21_netoFactura));
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    FacturaCompraDTO facturaCompraPresupuesto = FacturaCompraDTO.builder().build();
    facturaCompraPresupuesto.setObservaciones("Factura Compra Presupuesto test");
    facturaCompraPresupuesto.setFecha(new Date());
    facturaCompraPresupuesto.setTipoComprobante(TipoDeComprobante.PRESUPUESTO);
    facturaCompraPresupuesto.setRenglones(renglones);
    facturaCompraPresupuesto.setSubTotal(subTotal);
    facturaCompraPresupuesto.setRecargoPorcentaje(recargoPorcentaje);
    facturaCompraPresupuesto.setRecargoNeto(recargo_neto);
    facturaCompraPresupuesto.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaCompraPresupuesto.setDescuentoNeto(descuento_neto);
    facturaCompraPresupuesto.setSubTotalBruto(subTotalBruto);
    facturaCompraPresupuesto.setIva105Neto(iva_105_netoFactura);
    facturaCompraPresupuesto.setIva21Neto(iva_21_netoFactura);
    facturaCompraPresupuesto.setTotal(total);
    FacturaCompraDTO[] facturas =
      restTemplate.postForObject(
        apiPrefix
          + "/facturas/compra?"
          + "idProveedor=1&idEmpresa=1&idUsuario=2&idTransportista=1",
        facturaCompraPresupuesto,
        FacturaCompraDTO[].class);
    facturaCompraPresupuesto.setRazonSocialProveedor("Chamaco S.R.L.");
    assertEquals(facturaCompraPresupuesto, facturas[0]);
    ProveedorDTO proveedor =
      restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    UsuarioDTO usuario = restTemplate.getForObject(apiPrefix + "/usuarios/2", UsuarioDTO.class);
    TransportistaDTO transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", TransportistaDTO.class);
    assertEquals(proveedor.getRazonSocial(), facturas[0].getRazonSocialProveedor());
    assertEquals(empresa.getNombre(), facturas[0].getNombreEmpresa());
    assertEquals(
      usuario.getNombre() + " " + usuario.getApellido() + " (" + usuario.getUsername() + ")",
      facturas[0].getNombreUsuario());
    assertEquals(transportista.getNombre(), facturas[0].getNombreTransportista());
  }

  @Test
  void shouldCalcularPreciosDeProductosConRegargo() {
    ProductoDTO productoUno =
      new ProductoBuilder()
        .withCodigo("1")
        .withDescripcion("uno")
        .withCantidad(BigDecimal.TEN)
        .withBulto(BigDecimal.ONE)
        .withPrecioCosto(CIEN)
        .withGanancia_porcentaje(new BigDecimal("900"))
        .withGanancia_neto(new BigDecimal("900"))
        .withPrecioVentaPublico(new BigDecimal("1000"))
        .withIva_porcentaje(new BigDecimal("21.0"))
        .withIva_neto(new BigDecimal("210"))
        .withPrecioLista(new BigDecimal("1210"))
        .build();
    ProductoDTO productoDos =
      new ProductoBuilder()
        .withCodigo("2")
        .withDescripcion("dos")
        .withCantidad(new BigDecimal("6"))
        .withBulto(BigDecimal.ONE)
        .withPrecioCosto(CIEN)
        .withGanancia_porcentaje(new BigDecimal("900"))
        .withGanancia_neto(new BigDecimal("900"))
        .withPrecioVentaPublico(new BigDecimal("1000"))
        .withIva_porcentaje(new BigDecimal("10.5"))
        .withIva_neto(new BigDecimal("105"))
        .withPrecioLista(new BigDecimal("1105"))
        .build();
    restTemplate.postForObject(
        apiPrefix + "/productos?idMedida=1&idRubro=1&idProveedor=1&idEmpresa=1",
        productoUno,
        ProductoDTO.class);
    restTemplate.postForObject(
        apiPrefix + "/productos?idMedida=1&idRubro=1&idProveedor=1&idEmpresa=1",
        productoDos,
        ProductoDTO.class);
    long[] idsProductos = {1L, 2L};
    ProductosParaActualizarDTO productosParaActualizarDTO = ProductosParaActualizarDTO.builder()
      .idProducto(idsProductos).descuentoRecargoPorcentaje(BigDecimal.TEN).build();
    restTemplate.put(apiPrefix + "/productos/multiples", productosParaActualizarDTO);
    productoUno = restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    productoDos = restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals(new BigDecimal("110.000000000000000"), productoUno.getPrecioCosto());
    assertEquals(new BigDecimal("990.000000000000000"), productoUno.getGananciaNeto());
    assertEquals(new BigDecimal("1100.000000000000000"), productoUno.getPrecioVentaPublico());
    assertEquals(new BigDecimal("231.000000000000000"), productoUno.getIvaNeto());
    assertEquals(new BigDecimal("1331.000000000000000"), productoUno.getPrecioLista());
    assertEquals(new BigDecimal("110.000000000000000"), productoDos.getPrecioCosto());
    assertEquals(new BigDecimal("990.000000000000000"), productoDos.getGananciaNeto());
    assertEquals(new BigDecimal("1100.000000000000000"), productoDos.getPrecioVentaPublico());
    assertEquals(new BigDecimal("115.500000000000000"), productoDos.getIvaNeto());
    assertEquals(new BigDecimal("1215.500000000000000"), productoDos.getPrecioLista());
  }

  @Test
  void shouldCambiarPreciosProductos() {
    this.crearProductos();
    long[] idsProductos = {1L, 2L};
    ProductosParaActualizarDTO productosParaActualizarDTO = ProductosParaActualizarDTO.builder()
      .idProducto(idsProductos).precioCosto(new BigDecimal("150"))
      .gananciaPorcentaje(new BigDecimal("10"))
      .gananciaNeto(new BigDecimal("15"))
      .precioVentaPublico(new BigDecimal("165"))
      .ivaPorcentaje(BigDecimal.ZERO)
      .ivaNeto(BigDecimal.ZERO)
      .precioLista(new BigDecimal("165")).build();
    restTemplate.put(apiPrefix + "/productos/multiples", productosParaActualizarDTO);
    ProductoDTO productoUno = restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos = restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals(new BigDecimal("150.000000000000000"), productoUno.getPrecioCosto());
    assertEquals(new BigDecimal("150.000000000000000"), productoDos.getPrecioCosto());
    assertEquals(new BigDecimal("10.000000000000000"), productoUno.getGananciaPorcentaje());
    assertEquals(new BigDecimal("10.000000000000000"), productoDos.getGananciaPorcentaje());
    assertEquals(new BigDecimal("15.000000000000000"), productoUno.getGananciaNeto());
    assertEquals(new BigDecimal("15.000000000000000"), productoDos.getGananciaNeto());
    assertEquals(new BigDecimal("165.000000000000000"), productoUno.getPrecioVentaPublico());
    assertEquals(new BigDecimal("165.000000000000000"), productoDos.getPrecioVentaPublico());
    assertEquals(new BigDecimal("0E-15"), productoUno.getIvaPorcentaje());
    assertEquals(new BigDecimal("0E-15"), productoDos.getIvaPorcentaje());
    assertEquals(new BigDecimal("0E-15"), productoUno.getIvaNeto());
    assertEquals(new BigDecimal("0E-15"), productoDos.getIvaNeto());
    assertEquals(new BigDecimal("165.000000000000000"), productoDos.getPrecioLista());
    assertEquals(new BigDecimal("165.000000000000000"), productoDos.getPrecioLista());

  }

  @Test
  void shouldCambiarMedidasProductos() {
    this.crearProductos();
    long[] idsProductos = {1L, 2L};
    MedidaDTO medida = MedidaDTO.builder()
      .nombre("Distancia de Plank")
      .build();
    medida = restTemplate.postForObject(apiPrefix + "/medidas?idEmpresa=1", medida, MedidaDTO.class);
    ProductosParaActualizarDTO productosParaActualizarDTO = ProductosParaActualizarDTO.builder()
      .idProducto(idsProductos)
      .idMedida(medida.getId_Medida()).build();
    restTemplate.put(apiPrefix + "/productos/multiples", productosParaActualizarDTO);
    ProductoDTO productoUno = restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos = restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals("Distancia de Plank", productoUno.getNombreMedida());
    assertEquals("Distancia de Plank", productoDos.getNombreMedida());
  }

  @Test
  void shouldCambiarRubrosProductos() {
    this.crearProductos();
    long[] idsProductos = {1L, 2L};
    RubroDTO rubro = RubroDTO.builder()
      .nombre("Telefonía Galactica")
      .build();
    rubro = restTemplate.postForObject(apiPrefix + "/rubros?idEmpresa=1", rubro, RubroDTO.class);
    ProductosParaActualizarDTO productosParaActualizarDTO = ProductosParaActualizarDTO.builder()
      .idProducto(idsProductos)
      .idRubro(rubro.getId_Rubro()).build();
    restTemplate.put(apiPrefix + "/productos/multiples", productosParaActualizarDTO);
    ProductoDTO productoUno = restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos = restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals("Telefonía Galactica", productoUno.getNombreRubro());
    assertEquals("Telefonía Galactica", productoDos.getNombreRubro());
  }

  @Test
  void shouldCambiarProveedorProductos() {
    this.crearProductos();
    long[] idsProductos = {1L, 2L};
    ProveedorDTO proveedor =
      ProveedorDTO.builder()
        .codigo("QWE456")
        .razonSocial("Chamigo S.R.L.")
        .categoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO)
        .idFiscal(99999999999L)
        .telPrimario("379 59566333")
        .telSecundario("379 41122547")
        .contacto("Raul Jomer")
        .email("chamigosrl@gmail.com")
        .web("www.chamigosrl.com.ar")
        .eliminado(false)
        .saldoCuentaCorriente(BigDecimal.ZERO)
        .idEmpresa(1L)
        .build();
    proveedor = restTemplate.postForObject(apiPrefix + "/proveedores", proveedor, ProveedorDTO.class);
    ProductosParaActualizarDTO productosParaActualizarDTO = ProductosParaActualizarDTO.builder()
      .idProducto(idsProductos)
      .idProveedor(proveedor.getId_Proveedor()).build();
    restTemplate.put(apiPrefix + "/productos/multiples", productosParaActualizarDTO);
    ProductoDTO productoUno = restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos = restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals("Chamigo S.R.L.", productoUno.getRazonSocialProveedor());
    assertEquals("Chamigo S.R.L.", productoDos.getRazonSocialProveedor());
  }

  @Test
  void shouldCrearProductoConIva21() {
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    Rubro rubro = restTemplate.getForObject(apiPrefix + "/rubros/1", Rubro.class);
    ProveedorDTO proveedor =
      restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    Medida medida = restTemplate.getForObject(apiPrefix + "/medidas/1", Medida.class);
    NuevoProductoDTO productoUno =
      NuevoProductoDTO.builder()
        .codigo(RandomStringUtils.random(10, false, true))
        .descripcion(RandomStringUtils.random(10, true, false))
        .cantidad(BigDecimal.TEN)
        .bulto(BigDecimal.ONE)
        .precioCosto(CIEN)
        .gananciaPorcentaje(new BigDecimal("900"))
        .gananciaNeto(new BigDecimal("900"))
        .precioVentaPublico(new BigDecimal("1000"))
        .ivaPorcentaje(new BigDecimal("21.0"))
        .ivaNeto(new BigDecimal("210"))
        .precioLista(new BigDecimal("1210"))
        .nota("Producto Test")
        .build();
    ProductoDTO productoRecuperado =
      restTemplate.postForObject(
        apiPrefix
          + "/productos?idMedida="
          + medida.getId_Medida()
          + "&idRubro="
          + rubro.getId_Rubro()
          + "&idProveedor="
          + proveedor.getId_Proveedor()
          + "&idEmpresa="
          + empresa.getId_Empresa(),
        productoUno,
        ProductoDTO.class);
    assertEquals(productoUno.getCantidad(), productoRecuperado.getCantidad());
    assertEquals(productoUno.getIvaPorcentaje(), productoRecuperado.getIvaPorcentaje());
    assertEquals(productoUno.getIvaNeto(), productoRecuperado.getIvaNeto());
    assertEquals(productoUno.getCantMinima(), productoRecuperado.getCantMinima());
    assertEquals(productoUno.getBulto(), productoRecuperado.getBulto());
    assertEquals(productoUno.getCodigo(), productoRecuperado.getCodigo());
    assertEquals(productoUno.getDescripcion(), productoRecuperado.getDescripcion());
    assertEquals(productoUno.getGananciaNeto(), productoRecuperado.getGananciaNeto());
    assertEquals(productoUno.getGananciaPorcentaje(), productoRecuperado.getGananciaPorcentaje());
    assertEquals(productoUno.getPrecioLista(), productoRecuperado.getPrecioLista());
    assertEquals(productoUno.getPrecioVentaPublico(), productoRecuperado.getPrecioVentaPublico());
    assertEquals(productoUno.getPrecioCosto(), productoRecuperado.getPrecioCosto());
    assertEquals(new BigDecimal("21.0"), productoRecuperado.getIvaPorcentaje());
    assertEquals(new BigDecimal("210"), productoRecuperado.getIvaNeto());
  }

  @Test
  void shouldCrearProductoConIva105() {
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    Rubro rubro = restTemplate.getForObject(apiPrefix + "/rubros/1", Rubro.class);
    ProveedorDTO proveedor =
      restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    Medida medida = restTemplate.getForObject(apiPrefix + "/medidas/1", Medida.class);
    ProductoDTO productoUno =
      new ProductoBuilder()
        .withCodigo(RandomStringUtils.random(10, false, true))
        .withDescripcion(RandomStringUtils.random(10, true, false))
        .withCantidad(BigDecimal.TEN)
        .withPrecioCosto(CIEN)
        .withGanancia_porcentaje(new BigDecimal("900"))
        .withGanancia_neto(new BigDecimal("900"))
        .withPrecioVentaPublico(new BigDecimal("1000"))
        .withIva_porcentaje(new BigDecimal("10.5"))
        .withIva_neto(new BigDecimal("105"))
        .withPrecioLista(new BigDecimal("1105"))
        .withRazonSocialProveedor(proveedor.getRazonSocial())
        .withNombreRubro(rubro.getNombre())
        .withNombreMedida(medida.getNombre())
        .build();
    ProductoDTO productoRecuperado =
      restTemplate.postForObject(
        apiPrefix
          + "/productos?idMedida="
          + medida.getId_Medida()
          + "&idRubro="
          + rubro.getId_Rubro()
          + "&idProveedor="
          + proveedor.getId_Proveedor()
          + "&idEmpresa="
          + empresa.getId_Empresa(),
        productoUno,
        ProductoDTO.class);
    productoUno.setFechaAlta(productoRecuperado.getFechaAlta());
    productoUno.setIdProducto(productoRecuperado.getIdProducto());
    productoUno.setFechaUltimaModificacion(productoRecuperado.getFechaUltimaModificacion());
    assertEquals(productoUno, productoRecuperado);
    assertEquals(new BigDecimal("10.5"), productoRecuperado.getIvaPorcentaje());
    assertEquals(new BigDecimal("105"), productoRecuperado.getIvaNeto());
  }

  @Test
  void shouldNotCrearProductoDestacado() {
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    Rubro rubro = restTemplate.getForObject(apiPrefix + "/rubros/1", Rubro.class);
    ProveedorDTO proveedor =
        restTemplate.getForObject(apiPrefix + "/proveedores/1", ProveedorDTO.class);
    Medida medida = restTemplate.getForObject(apiPrefix + "/medidas/1", Medida.class);
    NuevoProductoDTO productoUno =
        NuevoProductoDTO.builder()
            .codigo(RandomStringUtils.random(10, false, true))
            .descripcion(RandomStringUtils.random(10, true, false))
            .cantidad(BigDecimal.TEN)
            .bulto(BigDecimal.ONE)
            .precioCosto(CIEN)
            .gananciaPorcentaje(new BigDecimal("900"))
            .gananciaNeto(new BigDecimal("900"))
            .precioVentaPublico(new BigDecimal("1000"))
            .ivaPorcentaje(new BigDecimal("21.0"))
            .ivaNeto(new BigDecimal("210"))
            .precioLista(new BigDecimal("1210"))
            .nota("Producto Test")
            .destacado(true)
            .build();
    ProductoDTO productoRecuperado =
        restTemplate.postForObject(
            apiPrefix
                + "/productos?idMedida="
                + medida.getId_Medida()
                + "&idRubro="
                + rubro.getId_Rubro()
                + "&idProveedor="
                + proveedor.getId_Proveedor()
                + "&idEmpresa="
                + empresa.getId_Empresa(),
            productoUno,
            ProductoDTO.class);
    assertFalse(productoRecuperado.isDestacado());
  }

  @Test
  void shouldModificarProducto() {
    this.shouldCrearProductoConIva21();
    ProductoDTO productoAModificar =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    productoAModificar.setDescripcion("PRODUCTO MODIFICADO.");
    productoAModificar.setCantidad(new BigDecimal("52"));
    productoAModificar.setCodigo("666");
    restTemplate.put(apiPrefix + "/productos?idMedida=2", productoAModificar);
    ProductoDTO productoModificado =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    assertEquals(productoAModificar, productoModificado);
  }

  @Test
  void shouldNotModificarProductoComoDestacadoSiEsPrivado() {
    this.shouldCrearProductoConIva21();
    ProductoDTO productoAModificar =
        restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    productoAModificar.setDescripcion("PRODUCTO MODIFICADO.");
    productoAModificar.setCantidad(new BigDecimal("52"));
    productoAModificar.setDestacado(true);
    productoAModificar.setUrlImagen(null);
    productoAModificar.setCodigo("666");
    try {
      restTemplate.put(apiPrefix + "/productos?idMedida=2", productoAModificar);
    } catch (RestClientResponseException ex) {
      assertTrue(ex.getMessage().startsWith("Para poder marcarlo como Destacado, debe ser Publico y tener imagen asignada."));
    }
  }

  @Test
  void shouldNotModificarProductoComoDestacadoSiNoTieneImagen() {
    this.shouldCrearProductoConIva21();
    ProductoDTO productoAModificar =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    productoAModificar.setDescripcion("PRODUCTO MODIFICADO.");
    productoAModificar.setCantidad(new BigDecimal("52"));
    productoAModificar.setPublico(false);
    productoAModificar.setDestacado(true);
    productoAModificar.setCodigo("666");
    try {
      restTemplate.put(apiPrefix + "/productos?idMedida=2", productoAModificar);
    } catch (RestClientResponseException ex) {
      assertTrue(ex.getMessage().startsWith("Para poder marcarlo como Destacado, debe ser Publico y tener imagen asignada."));
    }
  }

  @Test
  void shouldEliminarProducto() {
    this.shouldCrearProductoConIva21();
    restTemplate.delete(apiPrefix + "/productos?idProducto=1");
    try {
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    } catch (RestClientResponseException ex) {
      assertTrue(ex.getMessage().startsWith("El producto solicitado no existe."));
    }
  }

  @Test
  void shouldVerificarStockVenta() {
    this.shouldCrearFacturaVentaA();
    ProductoDTO producto1 =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO producto2 =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals(new BigDecimal("4.000000000000000"), producto1.getCantidad());
    assertEquals(new BigDecimal("3.000000000000000"), producto2.getCantidad());
  }

  @Test
  void shouldVerificarStockCompra() {
    this.shouldCrearFacturaCompraA();
    ProductoDTO producto1 =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO producto2 =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals(new BigDecimal("14.000000000000000"), producto1.getCantidad());
    assertEquals(new BigDecimal("9.000000000000000"), producto2.getCantidad());
  }

  @Disabled
  @Test
  void shouldBajaFacturaCompraCuandoLaCantidadEsNegativa() {
    this.shouldCrearFacturaCompraA();
    ProductoDTO producto1 =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO producto2 =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals(new BigDecimal("14.000000000000000"), producto1.getCantidad());
    assertEquals(new BigDecimal("9.000000000000000"), producto2.getCantidad());
    ProductoDTO productoUno =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO productoDos =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    RenglonFactura renglonUno =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoUno.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=14"
          + "&descuentoPorcentaje=10",
        RenglonFactura.class);
    RenglonFactura renglonDos =
      restTemplate.getForObject(
        apiPrefix
          + "/facturas/renglon?"
          + "idProducto="
          + productoDos.getIdProducto()
          + "&tipoDeComprobante="
          + TipoDeComprobante.FACTURA_A
          + "&movimiento="
          + Movimiento.VENTA
          + "&cantidad=9"
          + "&descuentoPorcentaje=5",
        RenglonFactura.class);
    List<RenglonFactura> renglones = new ArrayList<>();
    renglones.add(renglonUno);
    renglones.add(renglonDos);
    int size = renglones.size();
    BigDecimal[] cantidades = new BigDecimal[size];
    BigDecimal[] ivaPorcentajeRenglones = new BigDecimal[size];
    BigDecimal[] ivaNetoRenglones = new BigDecimal[size];
    int indice = 0;
    BigDecimal subTotal = BigDecimal.ZERO;
    for (RenglonFactura renglon : renglones) {
      subTotal = subTotal.add(renglon.getImporte());
      cantidades[indice] = renglon.getCantidad();
      ivaPorcentajeRenglones[indice] = renglon.getIvaPorcentaje();
      ivaNetoRenglones[indice] = renglon.getIvaNeto();
      indice++;
    }
    BigDecimal descuentoPorcentaje = new BigDecimal("25");
    BigDecimal recargoPorcentaje = new BigDecimal("10");
    BigDecimal descuento_neto =
      subTotal.multiply(descuentoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal recargo_neto =
      subTotal.multiply(recargoPorcentaje).divide(CIEN, 15, RoundingMode.HALF_UP);
    indice = cantidades.length;
    BigDecimal iva_105_netoFactura = BigDecimal.ZERO;
    BigDecimal iva_21_netoFactura = BigDecimal.ZERO;
    for (int i = 0; i < indice; i++) {
      if (ivaPorcentajeRenglones[i].compareTo(IVA_105) == 0) {
        iva_105_netoFactura =
          iva_105_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      } else if (ivaPorcentajeRenglones[i].compareTo(IVA_21) == 0) {
        iva_21_netoFactura =
          iva_21_netoFactura.add(
            cantidades[i].multiply(
              ivaNetoRenglones[i]
                .subtract(
                  ivaNetoRenglones[i].multiply(
                    descuentoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))
                .add(
                  ivaNetoRenglones[i].multiply(
                    recargoPorcentaje.divide(CIEN, 15, RoundingMode.HALF_UP)))));
      }
    }
    BigDecimal subTotalBruto = subTotal.add(recargo_neto).subtract(descuento_neto);
    BigDecimal total = subTotalBruto.add(iva_105_netoFactura).add(iva_21_netoFactura);
    Cliente cliente = restTemplate.getForObject(apiPrefix + "/clientes/1", Cliente.class);
    UsuarioDTO credencial = restTemplate.getForObject(apiPrefix + "/usuarios/1", UsuarioDTO.class);
    FacturaVentaDTO facturaVentaA =
      FacturaVentaDTO.builder().nombreFiscalCliente(cliente.getNombreFiscal()).build();
    facturaVentaA.setTipoComprobante(TipoDeComprobante.FACTURA_A);
    facturaVentaA.setRenglones(renglones);
    facturaVentaA.setSubTotal(subTotal);
    facturaVentaA.setRecargoPorcentaje(recargoPorcentaje);
    facturaVentaA.setRecargoNeto(recargo_neto);
    facturaVentaA.setDescuentoPorcentaje(descuentoPorcentaje);
    facturaVentaA.setDescuentoNeto(descuento_neto);
    facturaVentaA.setSubTotalBruto(subTotalBruto);
    facturaVentaA.setIva105Neto(iva_105_netoFactura);
    facturaVentaA.setIva21Neto(iva_21_netoFactura);
    facturaVentaA.setTotal(total);
    Transportista transportista =
      restTemplate.getForObject(apiPrefix + "/transportistas/1", Transportista.class);
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    restTemplate.postForObject(
      apiPrefix
        + "/facturas/venta?"
        + "idCliente="
        + cliente.getId_Cliente()
        + "&idEmpresa="
        + empresa.getId_Empresa()
        + "&idUsuario="
        + credencial.getId_Usuario()
        + "&idTransportista="
        + transportista.getId_Transportista(),
      facturaVentaA,
      FacturaVentaDTO[].class);
    restTemplate.delete(apiPrefix + "/facturas?idFactura=1");
    producto1 = restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    producto2 = restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals(BigDecimal.TEN, producto1.getCantidad());
    assertEquals(new BigDecimal("6.000000000000000"), producto2.getCantidad());
  }

  @Test
  void shouldCrearNotaCreditoVenta() {
    this.shouldCrearFacturaVentaB();
    List<FacturaVenta> facturasRecuperadas =
      restTemplate
        .exchange(
          apiPrefix
            + "/facturas/venta/busqueda/criteria?idEmpresa=1"
            + "&tipoFactura="
            + TipoDeComprobante.FACTURA_B
            + "&nroSerie=0"
            + "&nroFactura=1",
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<PaginaRespuestaRest<FacturaVenta>>() {
          })
        .getBody()
        .getContent();
    Long[] idsRenglonesFacura = new Long[]{1L};
    BigDecimal[] cantidades = new BigDecimal[]{new BigDecimal("5")};
    RenglonesDeFacturaParaNotaCreditoDTO renglonesDeFacturaParaNotaCreditoDTO =
      RenglonesDeFacturaParaNotaCreditoDTO.builder()
        .idsRenglonesFactura(idsRenglonesFacura)
        .cantidades(cantidades)
        .tipoDeComprobante(facturasRecuperadas.get(0).getTipoComprobante())
        .build();
    List<RenglonNotaCredito> renglonesNotaCredito =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix
            + "/notas/renglon/credito/producto",
          renglonesDeFacturaParaNotaCreditoDTO,
          RenglonNotaCredito[].class));
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    NotaCreditoDTO notaCredito = NotaCreditoDTO.builder().build();
    notaCredito.setNombreEmpresa(empresa.getNombre());
    notaCredito.setTipoComprobante(TipoDeComprobante.NOTA_CREDITO_B);
    notaCredito.setRenglonesNotaCredito(renglonesNotaCredito);
    notaCredito.setSubTotal(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/sub-total?importe="
          + renglonesNotaCredito.get(0).getImporteNeto(),
        BigDecimal.class));
    notaCredito.setRecargoPorcentaje(facturasRecuperadas.get(0).getRecargoPorcentaje());
    notaCredito.setRecargoNeto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/recargo-neto?subTotal="
          + notaCredito.getSubTotal()
          + "&recargoPorcentaje="
          + notaCredito.getRecargoPorcentaje(),
        BigDecimal.class));
    notaCredito.setDescuentoPorcentaje(facturasRecuperadas.get(0).getDescuentoPorcentaje());
    notaCredito.setDescuentoNeto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/descuento-neto?subTotal="
          + notaCredito.getSubTotal()
          + "&descuentoPorcentaje="
          + notaCredito.getDescuentoPorcentaje(),
        BigDecimal.class));
    notaCredito.setIva21Neto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/iva-neto?"
          + "tipoDeComprobante="
          + facturasRecuperadas.get(0).getTipoComprobante().name()
          + "&cantidades="
          + renglonesNotaCredito.get(0).getCantidad()
          + "&ivaPorcentajeRenglones="
          + renglonesNotaCredito.get(0).getIvaPorcentaje()
          + "&ivaNetoRenglones="
          + renglonesNotaCredito.get(0).getIvaNeto()
          + "&ivaPorcentaje=21"
          + "&descuentoPorcentaje="
          + facturasRecuperadas.get(0).getDescuentoPorcentaje()
          + "&recargoPorcentaje="
          + facturasRecuperadas.get(0).getRecargoPorcentaje(),
        BigDecimal.class));
    notaCredito.setIva105Neto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/iva-neto?"
          + "tipoDeComprobante="
          + facturasRecuperadas.get(0).getTipoComprobante().name()
          + "&cantidades="
          + renglonesNotaCredito.get(0).getCantidad()
          + "&ivaPorcentajeRenglones="
          + renglonesNotaCredito.get(0).getIvaPorcentaje()
          + "&ivaNetoRenglones="
          + renglonesNotaCredito.get(0).getIvaNeto()
          + "&ivaPorcentaje=10.5"
          + "&descuentoPorcentaje="
          + facturasRecuperadas.get(0).getDescuentoPorcentaje()
          + "&recargoPorcentaje="
          + facturasRecuperadas.get(0).getRecargoPorcentaje(),
        BigDecimal.class));
    notaCredito.setSubTotalBruto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/sub-total-bruto?"
          + "tipoDeComprobante="
          + facturasRecuperadas.get(0).getTipoComprobante().name()
          + "&subTotal="
          + notaCredito.getSubTotal()
          + "&recargoNeto="
          + notaCredito.getRecargoNeto()
          + "&descuentoNeto="
          + notaCredito.getDescuentoNeto()
          + "&iva21Neto="
          + notaCredito.getIva21Neto()
          + "&iva105Neto="
          + notaCredito.getIva105Neto(),
        BigDecimal.class));
    notaCredito.setTotal(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/total?subTotalBruto="
          + notaCredito.getSubTotalBruto()
          + "&iva21Neto="
          + notaCredito.getIva21Neto()
          + "&iva105Neto="
          + notaCredito.getIva105Neto(),
        BigDecimal.class));
    notaCredito.setMotivo("Devolución - Nota crédito venta");
    notaCredito.setModificaStock(true);
    NotaCreditoDTO notaGuardada =
      restTemplate.postForObject(
        apiPrefix
          + "/notas/credito/empresa/1/usuario/1/factura/1?idCliente=1&movimiento=VENTA&modificarStock=true",
        notaCredito,
        NotaCreditoDTO.class);
    notaCredito.setNroNota(notaGuardada.getNroNota());
    assertEquals(notaCredito, notaGuardada);
    restTemplate.getForObject(apiPrefix + "/notas/1/reporte", byte[].class);
  }

  @Test
  void shouldVerificarStockNotaCreditoVenta() {
    this.shouldCrearNotaCreditoVenta();
    ProductoDTO producto1 =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO producto2 =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals(new BigDecimal("10.000000000000000"), producto1.getCantidad());
    assertEquals(new BigDecimal("4.000000000000000"), producto2.getCantidad());
  }

  @Test
  void shouldCrearNotaCreditoCompra() {
    this.shouldCrearFacturaCompraB();
    Long[] idsRenglonesFacura = new Long[]{1L};
    BigDecimal[] cantidades = new BigDecimal[]{new BigDecimal("3")};
    RenglonesDeFacturaParaNotaCreditoDTO renglonesDeFacturaParaNotaCreditoDTO =
      RenglonesDeFacturaParaNotaCreditoDTO.builder()
        .idsRenglonesFactura(idsRenglonesFacura)
        .cantidades(cantidades)
        .tipoDeComprobante(TipoDeComprobante.FACTURA_B)
        .build();
    List<RenglonNotaCredito> renglonesNotaCredito =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix
            + "/notas/renglon/credito/producto",
          renglonesDeFacturaParaNotaCreditoDTO,
          RenglonNotaCredito[].class));
    EmpresaDTO empresa = restTemplate.getForObject(apiPrefix + "/empresas/1", EmpresaDTO.class);
    NotaCreditoDTO notaCreditoProveedor = NotaCreditoDTO.builder().build();
    notaCreditoProveedor.setTipoComprobante(TipoDeComprobante.NOTA_CREDITO_B);
    notaCreditoProveedor.setNombreEmpresa(empresa.getNombre());
    notaCreditoProveedor.setRenglonesNotaCredito(renglonesNotaCredito);
    notaCreditoProveedor.setFecha(new Date());
    notaCreditoProveedor.setModificaStock(true);
    notaCreditoProveedor.setSubTotal(
        restTemplate.getForObject(
            apiPrefix
                + "/notas/credito/sub-total?importe="
                + renglonesNotaCredito.get(0).getImporteNeto(),
            BigDecimal.class));
    notaCreditoProveedor.setRecargoPorcentaje(BigDecimal.TEN);
    notaCreditoProveedor.setRecargoNeto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/recargo-neto?subTotal="
          + notaCreditoProveedor.getSubTotal()
          + "&recargoPorcentaje="
          + notaCreditoProveedor.getRecargoPorcentaje(),
        BigDecimal.class));
    notaCreditoProveedor.setDescuentoPorcentaje(new BigDecimal("25"));
    notaCreditoProveedor.setDescuentoNeto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/descuento-neto?subTotal="
          + notaCreditoProveedor.getSubTotal()
          + "&descuentoPorcentaje="
          + notaCreditoProveedor.getDescuentoPorcentaje(),
        BigDecimal.class));
    notaCreditoProveedor.setIva21Neto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/iva-neto?"
          + "tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&cantidades="
          + renglonesNotaCredito.get(0).getCantidad()
          + "&ivaPorcentajeRenglones="
          + renglonesNotaCredito.get(0).getIvaPorcentaje()
          + "&ivaNetoRenglones="
          + renglonesNotaCredito.get(0).getIvaNeto()
          + "&ivaPorcentaje=21"
          + "&descuentoPorcentaje=25"
          + "&recargoPorcentaje=10",
        BigDecimal.class));
    notaCreditoProveedor.setIva105Neto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/iva-neto?"
          + "tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&cantidades="
          + renglonesNotaCredito.get(0).getCantidad()
          + "&ivaPorcentajeRenglones="
          + renglonesNotaCredito.get(0).getIvaPorcentaje()
          + "&ivaNetoRenglones="
          + renglonesNotaCredito.get(0).getIvaNeto()
          + "&ivaPorcentaje=10.5"
          + "&descuentoPorcentaje=25"
          + "&recargoPorcentaje=10",
        BigDecimal.class));
    notaCreditoProveedor.setSubTotalBruto(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/sub-total-bruto?"
          + "tipoDeComprobante="
          + TipoDeComprobante.FACTURA_B
          + "&subTotal="
          + notaCreditoProveedor.getSubTotal()
          + "&recargoNeto="
          + notaCreditoProveedor.getRecargoNeto()
          + "&descuentoNeto="
          + notaCreditoProveedor.getDescuentoNeto()
          + "&iva21Neto="
          + notaCreditoProveedor.getIva21Neto()
          + "&iva105Neto="
          + notaCreditoProveedor.getIva105Neto(),
        BigDecimal.class));
    notaCreditoProveedor.setTotal(
      restTemplate.getForObject(
        apiPrefix
          + "/notas/credito/total?subTotalBruto="
          + notaCreditoProveedor.getSubTotalBruto()
          + "&iva21Neto="
          + notaCreditoProveedor.getIva21Neto()
          + "&iva105Neto="
          + notaCreditoProveedor.getIva105Neto(),
        BigDecimal.class));
    notaCreditoProveedor.setMotivo("Devolución");
    NotaCreditoDTO notaCreditoRecuperada =
      restTemplate.postForObject(
        apiPrefix
          + "/notas/credito/empresa/1/usuario/1/factura/1?idProveedor=1&movimiento=COMPRA&modificarStock=true",
        notaCreditoProveedor,
        NotaCreditoDTO.class);
    assertEquals(notaCreditoProveedor, notaCreditoRecuperada);
  }

  @Test
  void shouldVerificarStockNotaCreditoCompra() {
    this.shouldCrearNotaCreditoCompra();
    ProductoDTO producto1 =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    ProductoDTO producto2 =
      restTemplate.getForObject(apiPrefix + "/productos/2", ProductoDTO.class);
    assertEquals(new BigDecimal("12.000000000000000"), producto1.getCantidad());
    assertEquals(new BigDecimal("8.000000000000000"), producto2.getCantidad());
  }

  @Test
  void shouldComprobarSaldoCuentaCorrienteCliente() {
    this.abrirCaja();
    this.shouldCrearFacturaVentaB();
    assertEquals(
      new BigDecimal("-5992.500000000000000"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class));
    this.crearReciboParaCliente(5992.5);
    assertEquals(
      new BigDecimal("0E-15"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class));
    this.crearNotaDebitoParaCliente();
    assertEquals(
      new BigDecimal("-6113.500000000000000"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class));
    this.crearReciboParaCliente(6113.5);
    assertEquals(
      new BigDecimal("0E-15"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class));
    this.crearNotaCreditoParaCliente();
    assertEquals(
      new BigDecimal("4114.000000000000000"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class));
    this.shouldCrearFacturaVentaA();
    assertEquals(
      new BigDecimal("-4116.762500000000000"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1/saldo", BigDecimal.class));
  }

  @Test
  void shouldComprobarSaldoParcialCuentaCorrienteCliente() {
    this.abrirCaja();
    this.shouldCrearFacturaVentaB();
    this.crearReciboParaCliente(5992.5);
    this.crearNotaDebitoParaCliente();
    this.crearReciboParaCliente(6113.5);
    this.crearNotaCreditoParaCliente();
    List<RenglonCuentaCorriente> renglonesCuentaCorriente =
      restTemplate
        .exchange(
          apiPrefix + "/cuentas-corriente/1/renglones" + "?pagina=" + 0 + "&tamanio=" + 50,
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<PaginaRespuestaRest<RenglonCuentaCorriente>>() {
          })
        .getBody()
        .getContent();
    assertEquals(new Double(4114), renglonesCuentaCorriente.get(0).getSaldo());
    assertEquals(new Double(0), renglonesCuentaCorriente.get(1).getSaldo());
    assertEquals(new Double(-6113.5), renglonesCuentaCorriente.get(2).getSaldo());
    assertEquals(new Double(0), renglonesCuentaCorriente.get(3).getSaldo());
    assertEquals(new Double(-5992.5), renglonesCuentaCorriente.get(4).getSaldo());
  }

  @Test
  void shouldCrearPedido() {
    this.crearProductos();
    List<NuevoRenglonPedidoDTO> renglonesPedidoDTO = new ArrayList<>();
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(1L)
        .cantidad(new BigDecimal("5.000000000000000"))
        .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
        .build());
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(2L)
        .cantidad(new BigDecimal("2.000000000000000"))
        .descuentoPorcentaje(BigDecimal.ZERO)
        .build());
    List<RenglonPedidoDTO> renglonesPedido =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix + "/pedidos/renglones", renglonesPedidoDTO, RenglonPedidoDTO[].class));
    BigDecimal importe = BigDecimal.ZERO;
    for (RenglonPedidoDTO renglon : renglonesPedido) {
      importe = importe.add(renglon.getImporte()).setScale(5, RoundingMode.HALF_UP);
    }
    BigDecimal recargoNeto =
      importe.multiply(new BigDecimal("5")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal descuentoNeto =
      importe.multiply(new BigDecimal("15")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal total = importe.add(recargoNeto).subtract(descuentoNeto);
    NuevoPedidoDTO nuevoPedidoDTO =
      NuevoPedidoDTO.builder()
        .descuentoNeto(descuentoNeto)
        .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
        .recargoNeto(recargoNeto)
        .recargoPorcentaje(new BigDecimal("5"))
        .fechaVencimiento(new Date())
        .observaciones("Nuevo Pedido Test")
        .renglones(renglonesPedido)
        .subTotal(importe)
        .total(total)
        .idEmpresa(1L)
        .idUsuario(2L)
        .idCliente(1L)
        .tipoDeEnvio(TipoDeEnvio.USAR_UBICACION_FACTURACION)
        .build();
    PedidoDTO pedidoRecuperado =
      restTemplate.postForObject(
        apiPrefix + "/pedidos",
        nuevoPedidoDTO,
        PedidoDTO.class);
    assertEquals(nuevoPedidoDTO.getTotal(), pedidoRecuperado.getTotalEstimado());
    assertEquals(pedidoRecuperado.getObservaciones(), nuevoPedidoDTO.getObservaciones());
    assertEquals(EstadoPedido.ABIERTO, pedidoRecuperado.getEstado());
  }

  @Test
  void shouldCrearPedidoConUbicacionFacturacion() {
    this.crearProductos();
    List<NuevoRenglonPedidoDTO> renglonesPedidoDTO = new ArrayList<>();
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(1L)
        .cantidad(new BigDecimal("5.000000000000000"))
        .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
        .build());
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(2L)
        .cantidad(new BigDecimal("2.000000000000000"))
        .descuentoPorcentaje(BigDecimal.ZERO)
        .build());
    List<RenglonPedidoDTO> renglonesPedido =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix + "/pedidos/renglones", renglonesPedidoDTO, RenglonPedidoDTO[].class));
    BigDecimal importe = BigDecimal.ZERO;
    for (RenglonPedidoDTO renglon : renglonesPedido) {
      importe = importe.add(renglon.getImporte()).setScale(5, RoundingMode.HALF_UP);
    }
    BigDecimal recargoNeto =
      importe.multiply(new BigDecimal("5")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal descuentoNeto =
      importe.multiply(new BigDecimal("15")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal total = importe.add(recargoNeto).subtract(descuentoNeto);
    NuevoPedidoDTO nuevoPedidoDTO =
      NuevoPedidoDTO.builder()
        .descuentoNeto(descuentoNeto)
        .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
        .recargoNeto(recargoNeto)
        .recargoPorcentaje(new BigDecimal("5"))
        .fechaVencimiento(new Date())
        .observaciones("Nuevo Pedido Test")
        .renglones(renglonesPedido)
        .subTotal(importe)
        .total(total)
        .idEmpresa(1L)
        .idUsuario(2L)
        .idCliente(1L)
        .tipoDeEnvio(TipoDeEnvio.USAR_UBICACION_FACTURACION)
        .build();
    PedidoDTO pedidoRecuperado =
      restTemplate.postForObject(
        apiPrefix + "/pedidos",
        nuevoPedidoDTO,
        PedidoDTO.class);
    assertEquals("Rio Parana 14500 Corrientes Corrientes", pedidoRecuperado.getDetalleEnvio());
  }

  @Test
  void shouldCrearPedidoConUbicacionEnvio() {
    this.crearProductos();
    List<NuevoRenglonPedidoDTO> renglonesPedidoDTO = new ArrayList<>();
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(1L)
        .cantidad(new BigDecimal("5.000000000000000"))
        .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
        .build());
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(2L)
        .cantidad(new BigDecimal("2.000000000000000"))
        .descuentoPorcentaje(BigDecimal.ZERO)
        .build());
    List<RenglonPedidoDTO> renglonesPedido =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix + "/pedidos/renglones", renglonesPedidoDTO, RenglonPedidoDTO[].class));
    BigDecimal importe = BigDecimal.ZERO;
    for (RenglonPedidoDTO renglon : renglonesPedido) {
      importe = importe.add(renglon.getImporte()).setScale(5, RoundingMode.HALF_UP);
    }
    BigDecimal recargoNeto =
      importe.multiply(new BigDecimal("5")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal descuentoNeto =
      importe.multiply(new BigDecimal("15")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal total = importe.add(recargoNeto).subtract(descuentoNeto);
    NuevoPedidoDTO nuevoPedidoDTO =
      NuevoPedidoDTO.builder()
        .descuentoNeto(descuentoNeto)
        .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
        .recargoNeto(recargoNeto)
        .recargoPorcentaje(new BigDecimal("5"))
        .fechaVencimiento(new Date())
        .observaciones("Nuevo Pedido Test")
        .renglones(renglonesPedido)
        .subTotal(importe)
        .total(total)
        .idEmpresa(1L)
        .idUsuario(2L)
        .idCliente(1L)
        .tipoDeEnvio(TipoDeEnvio.USAR_UBICACION_ENVIO)
        .build();
    PedidoDTO pedidoRecuperado =
      restTemplate.postForObject(
        apiPrefix + "/pedidos",
        nuevoPedidoDTO,
        PedidoDTO.class);
    assertEquals("Rio Uruguay 15000 Corrientes Corrientes", pedidoRecuperado.getDetalleEnvio());
  }

  @Test
  void shouldCrearPedidoConUbicacionSucursal() {
    this.crearProductos();
    this.shouldCrearEmpresaResponsableInscripto();
    List<NuevoRenglonPedidoDTO> renglonesPedidoDTO = new ArrayList<>();
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(1L)
        .cantidad(new BigDecimal("5.000000000000000"))
        .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
        .build());
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(2L)
        .cantidad(new BigDecimal("2.000000000000000"))
        .descuentoPorcentaje(BigDecimal.ZERO)
        .build());
    List<RenglonPedidoDTO> renglonesPedido =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix + "/pedidos/renglones", renglonesPedidoDTO, RenglonPedidoDTO[].class));
    BigDecimal importe = BigDecimal.ZERO;
    for (RenglonPedidoDTO renglon : renglonesPedido) {
      importe = importe.add(renglon.getImporte()).setScale(5, RoundingMode.HALF_UP);
    }
    BigDecimal recargoNeto =
      importe.multiply(new BigDecimal("5")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal descuentoNeto =
      importe.multiply(new BigDecimal("15")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal total = importe.add(recargoNeto).subtract(descuentoNeto);
    NuevoPedidoDTO nuevoPedidoDTO =
      NuevoPedidoDTO.builder()
        .descuentoNeto(descuentoNeto)
        .descuentoPorcentaje(new BigDecimal("15.000000000000000"))
        .recargoNeto(recargoNeto)
        .recargoPorcentaje(new BigDecimal("5"))
        .fechaVencimiento(new Date())
        .observaciones("Nuevo Pedido Test")
        .renglones(renglonesPedido)
        .subTotal(importe)
        .total(total)
        .idEmpresa(1L)
        .idUsuario(2L)
        .idCliente(1L)
        .tipoDeEnvio(TipoDeEnvio.RETIRO_EN_SUCURSAL)
        .idSucursal(2L)
        .build();
    PedidoDTO pedidoRecuperado =
      restTemplate.postForObject(
        apiPrefix + "/pedidos",
        nuevoPedidoDTO,
        PedidoDTO.class);
    EmpresaDTO empresaDTO = restTemplate.getForObject(apiPrefix + "/empresas/2", EmpresaDTO.class);
    assertEquals("Rio Piacentin 345 Corrientes Corrientes", pedidoRecuperado.getDetalleEnvio());
  }

  @Test
  void shouldFacturarPedido() {
    this.shouldCrearPedido();
    this.crearFacturaTipoADePedido();
    PedidoDTO pedidoRecuperado =
      restTemplate.getForObject(apiPrefix + "/pedidos/1", PedidoDTO.class);
    pedidoRecuperado =
      restTemplate.getForObject(
        apiPrefix + "/pedidos/" + pedidoRecuperado.getId_Pedido(), PedidoDTO.class);
    assertEquals(EstadoPedido.ACTIVO, pedidoRecuperado.getEstado());
    this.crearFacturaTipoBDePedido();
    pedidoRecuperado =
      restTemplate.getForObject(
        apiPrefix + "/pedidos/" + pedidoRecuperado.getId_Pedido(), PedidoDTO.class);
    assertEquals(EstadoPedido.CERRADO, pedidoRecuperado.getEstado());
  }

  @Test
  void shouldModificarPedido() {
    this.crearProductos();
    List<NuevoRenglonPedidoDTO> renglonesPedidoDTO = new ArrayList<>();
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(1L)
        .cantidad(new BigDecimal("5"))
        .descuentoPorcentaje(new BigDecimal("15"))
        .build());
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(2L)
        .cantidad(new BigDecimal("2"))
        .descuentoPorcentaje(BigDecimal.ZERO)
        .build());
    List<RenglonPedidoDTO> renglonesPedido =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix + "/pedidos/renglones", renglonesPedidoDTO, RenglonPedidoDTO[].class));
    BigDecimal importe = BigDecimal.ZERO;
    for (RenglonPedidoDTO renglon : renglonesPedido) {
      importe = importe.add(renglon.getImporte());
    }
    BigDecimal recargoNeto =
      importe.multiply(new BigDecimal("5")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal descuentoNeto =
      importe.multiply(new BigDecimal("15")).divide(CIEN, 15, RoundingMode.HALF_UP);
    BigDecimal total = importe.add(recargoNeto).subtract(descuentoNeto);
    NuevoPedidoDTO nuevoPedidoDTO =
      NuevoPedidoDTO.builder()
        .descuentoNeto(descuentoNeto)
        .descuentoPorcentaje(new BigDecimal("15"))
        .recargoNeto(recargoNeto)
        .recargoPorcentaje(new BigDecimal("5"))
        .fechaVencimiento(new Date())
        .observaciones("Nuevo Pedido Test")
        .renglones(renglonesPedido)
        .subTotal(importe)
        .total(total)
        .idEmpresa(1L)
        .idUsuario(1L)
        .idCliente(1L)
        .tipoDeEnvio(TipoDeEnvio.USAR_UBICACION_FACTURACION)
        .build();
    PedidoDTO pedidoRecuperado =
      restTemplate.postForObject(
        apiPrefix + "/pedidos",
        nuevoPedidoDTO,
        PedidoDTO.class);
    assertEquals(nuevoPedidoDTO.getTotal(), pedidoRecuperado.getTotalEstimado());
    assertEquals(nuevoPedidoDTO.getObservaciones(), pedidoRecuperado.getObservaciones());
    assertEquals(EstadoPedido.ABIERTO, pedidoRecuperado.getEstado());
    this.crearProductos();
    renglonesPedidoDTO = new ArrayList<>();
    renglonesPedidoDTO.add(
      NuevoRenglonPedidoDTO.builder()
        .idProductoItem(3)
        .cantidad(new BigDecimal("7"))
        .descuentoPorcentaje(new BigDecimal("18"))
        .build());
    renglonesPedido =
      Arrays.asList(
        restTemplate.postForObject(
          apiPrefix + "/pedidos/renglones", renglonesPedidoDTO, RenglonPedidoDTO[].class));
    importe = BigDecimal.ZERO;
    for (RenglonPedidoDTO renglon : renglonesPedido) {
      importe = importe.add(renglon.getImporte());
    }
    recargoNeto = importe.multiply(new BigDecimal("5")).divide(CIEN, 15, RoundingMode.HALF_UP);
    descuentoNeto = importe.multiply(new BigDecimal("15")).divide(CIEN, 15, RoundingMode.HALF_UP);
    total = importe.add(recargoNeto).subtract(descuentoNeto);
    pedidoRecuperado.setSubTotal(importe);
    pedidoRecuperado.setRecargoNeto(recargoNeto);
    pedidoRecuperado.setDescuentoNeto(descuentoNeto);
    pedidoRecuperado.setTotalActual(total);
    pedidoRecuperado.setTotalEstimado(total);
    pedidoRecuperado.setRenglones(renglonesPedido);
    pedidoRecuperado.setObservaciones("Cambiando las observaciones del pedido");
    restTemplate.put(apiPrefix + "/pedidos?idEmpresa=1&idCliente=1&idUsuario=1&tipoDeEnvio=USAR_UBICACION_FACTURACION", pedidoRecuperado);
    PedidoDTO pedidoModificado =
      restTemplate.getForObject(apiPrefix + "/pedidos/1", PedidoDTO.class);
    assertEquals(pedidoRecuperado, pedidoModificado);
    assertEquals(total, pedidoModificado.getTotalEstimado());
    assertEquals("Cambiando las observaciones del pedido", pedidoModificado.getObservaciones());
    assertEquals(EstadoPedido.ABIERTO, pedidoModificado.getEstado());
  }

  @Test
  void shouldVerificarTransicionDeEstadosDeUnPedido() {
    this.shouldCrearPedido();
    this.crearFacturaTipoADePedido();
    PedidoDTO pedidoRecuperado =
      restTemplate.getForObject(apiPrefix + "/pedidos/1", PedidoDTO.class);
    pedidoRecuperado =
      restTemplate.getForObject(
        apiPrefix + "/pedidos/" + pedidoRecuperado.getId_Pedido(), PedidoDTO.class);
    assertEquals(EstadoPedido.ACTIVO, pedidoRecuperado.getEstado());
    this.crearFacturaTipoBDePedido();
    List<FacturaVenta> facturasRecuperadas =
      restTemplate
        .exchange(
          apiPrefix
            + "/facturas/venta/busqueda/criteria?"
            + "idEmpresa=1"
            + "&nroPedido="
            + pedidoRecuperado.getNroPedido(),
          HttpMethod.GET,
          null,
          new ParameterizedTypeReference<PaginaRespuestaRest<FacturaVenta>>() {
          })
        .getBody()
        .getContent();
    pedidoRecuperado =
      restTemplate.getForObject(
        apiPrefix + "/pedidos/" + pedidoRecuperado.getId_Pedido(), PedidoDTO.class);
    assertEquals(EstadoPedido.CERRADO, pedidoRecuperado.getEstado());
  }

  @Test
  void shouldComprobarSaldoCuentaCorrienteProveedor() {
    this.abrirCaja();
    this.shouldCrearFacturaCompraB();
    assertEquals(
      new BigDecimal("-599.250000000000000"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1/saldo", BigDecimal.class));
    this.crearReciboParaProveedor(599.25);
    assertEquals(
      0,
      restTemplate
        .getForObject(apiPrefix + "/cuentas-corriente/proveedores/1/saldo", BigDecimal.class)
        .doubleValue(),
      0);
    restTemplate.delete(apiPrefix + "/recibos/1");
    assertEquals(
      new BigDecimal("-599.250000000000000"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1/saldo", BigDecimal.class));
    this.crearReciboParaProveedor(499.25);
    assertEquals(
      new BigDecimal("-100.000000000000000"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1/saldo", BigDecimal.class));
    this.crearReciboParaProveedor(200);
    assertEquals(
      new BigDecimal("100.000000000000000"),
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1/saldo", BigDecimal.class));
  }

  @Test
  void shouldComprobarSaldoParcialCuentaCorrienteProveedor() {
    this.abrirCaja();
    this.shouldCrearFacturaCompraB();
    this.crearReciboParaProveedor(599.25);
    restTemplate.delete(apiPrefix + "/recibos/1");
    this.crearReciboParaProveedor(499.25);
    this.crearReciboParaProveedor(200);
    this.shouldCrearFacturaCompraB();
    this.crearNotaCreditoParaProveedor();
    List<RenglonCuentaCorriente> renglonesCuentaCorriente =
        restTemplate
            .exchange(
                apiPrefix + "/cuentas-corriente/2/renglones" + "?pagina=" + 0 + "&tamanio=" + 50,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<PaginaRespuestaRest<RenglonCuentaCorriente>>() {})
            .getBody()
            .getContent();
    assertEquals(-87.85, renglonesCuentaCorriente.get(0).getSaldo(), 0);
    assertEquals(-499.25, renglonesCuentaCorriente.get(1).getSaldo(), 0);
    assertEquals(100, renglonesCuentaCorriente.get(2).getSaldo(), 0);
    assertEquals(-100, renglonesCuentaCorriente.get(3).getSaldo(), 0);
    assertEquals(-599.25, renglonesCuentaCorriente.get(4).getSaldo(), 0);
    this.crearNotaDebitoParaProveedor();
    renglonesCuentaCorriente =
        restTemplate
            .exchange(
                apiPrefix + "/cuentas-corriente/2/renglones" + "?pagina=" + 0 + "&tamanio=" + 50,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<PaginaRespuestaRest<RenglonCuentaCorriente>>() {})
            .getBody()
            .getContent();
    assertEquals(-408.85, renglonesCuentaCorriente.get(0).getSaldo(), 0);
    assertEquals(-87.85, renglonesCuentaCorriente.get(1).getSaldo(), 0);
    assertEquals(-499.25, renglonesCuentaCorriente.get(2).getSaldo(), 0);
    assertEquals(100, renglonesCuentaCorriente.get(3).getSaldo(), 0);
    assertEquals(-100, renglonesCuentaCorriente.get(4).getSaldo(), 0);
    assertEquals(-599.25, renglonesCuentaCorriente.get(5).getSaldo(), 0);
  }

  @Test
  void shouldCrearUsuario() {
    UsuarioDTO nuevoUsuario =
      UsuarioDTO.builder()
        .username("wicca")
        .password("Salem123")
        .nombre("Sabrina")
        .apellido("Spellman")
        .email("Witch@gmail.com")
        .roles(Collections.singletonList(Rol.ENCARGADO))
        .habilitado(true)
        .build();
    restTemplate.postForObject(apiPrefix + "/usuarios", nuevoUsuario, UsuarioDTO.class);
    UsuarioDTO usuarioRecuperado =
      restTemplate.getForObject(apiPrefix + "/usuarios/3", UsuarioDTO.class);
    assertEquals(nuevoUsuario, usuarioRecuperado);
  }

  @Test
  void shouldModificarUsuario() {
    this.shouldCrearUsuario();
    UsuarioDTO usuarioRecuperado =
      restTemplate.getForObject(apiPrefix + "/usuarios/2", UsuarioDTO.class);
    usuarioRecuperado.setUsername("darkmagic");
    Rol[] roles = new Rol[]{Rol.ADMINISTRADOR, Rol.ENCARGADO};
    usuarioRecuperado.setRoles(Arrays.asList(roles));
    restTemplate.put(apiPrefix + "/usuarios", usuarioRecuperado);
    UsuarioDTO usuarioModificado =
      restTemplate.getForObject(apiPrefix + "/usuarios/2", UsuarioDTO.class);
    assertEquals(usuarioRecuperado, usuarioModificado);
  }

  @Test
  void shouldValidarPermisosUsuarioAlEliminarProveedor() {
    UsuarioDTO nuevoUsuario =
      UsuarioDTO.builder()
        .username("wicca")
        .password("Salem123")
        .nombre("Sabrina")
        .apellido("Spellman")
        .email("Witch@gmail.com")
        .roles(Collections.singletonList(Rol.VENDEDOR))
        .habilitado(true)
        .build();
    restTemplate.postForObject(apiPrefix + "/usuarios", nuevoUsuario, UsuarioDTO.class);
    this.token =
      restTemplate
        .postForEntity(apiPrefix + "/login", new Credencial("wicca", "Salem123"), String.class)
        .getBody();
    try {
      restTemplate.delete(apiPrefix + "/proveedores/1");
    } catch (RestClientResponseException ex) {
      assertTrue(ex.getMessage().startsWith("No posee permisos para realizar esta operación"));
    }
  }

  @Test
  void shouldActualizarFechaUltimaModificacionCuentaCorrienteCliente() {
    this.abrirCaja();
    shouldCrearFacturaVentaB();
    CuentaCorriente ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1", CuentaCorriente.class);
    FacturaVentaDTO facturaVentaDTO =
      restTemplate.getForObject(apiPrefix + "/facturas/1", FacturaVentaDTO.class);
    assertEquals(facturaVentaDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
    this.crearReciboParaCliente(5992.5);
    ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1", CuentaCorriente.class);
    ReciboDTO reciboDTO = restTemplate.getForObject(apiPrefix + "/recibos/1", ReciboDTO.class);
    assertEquals(reciboDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
    this.crearNotaDebitoParaCliente();
    ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1", CuentaCorriente.class);
    NotaDebitoDTO notaDebitoDTO =
      restTemplate.getForObject(apiPrefix + "/notas/1", NotaDebitoDTO.class);
    assertEquals(notaDebitoDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
    this.crearReciboParaCliente(6113.5);
    ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1", CuentaCorriente.class);
    reciboDTO = restTemplate.getForObject(apiPrefix + "/recibos/2", ReciboDTO.class);
    assertEquals(reciboDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
    this.crearNotaCreditoParaCliente();
    ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/clientes/1", CuentaCorriente.class);
    NotaCreditoDTO notaCreditoDTO =
      restTemplate.getForObject(apiPrefix + "/notas/2", NotaCreditoDTO.class);
    assertEquals(notaCreditoDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
  }

  @Test
  void shouldActualizarFechaUltimaModificacionCuentaCorrienteProveedor() {
    this.abrirCaja();
    shouldCrearFacturaCompraB();
    CuentaCorriente ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1", CuentaCorriente.class);
    FacturaCompraDTO facturaCompraDTO =
      restTemplate.getForObject(apiPrefix + "/facturas/1", FacturaCompraDTO.class);
    assertEquals(facturaCompraDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
    this.crearReciboParaProveedor(599.25);
    ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1", CuentaCorriente.class);
    ReciboDTO reciboDTO = restTemplate.getForObject(apiPrefix + "/recibos/1", ReciboDTO.class);
    assertEquals(reciboDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
    restTemplate.delete(apiPrefix + "/recibos/1");
    ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1", CuentaCorriente.class);
    assertEquals(facturaCompraDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
    this.crearReciboParaProveedor(499.25);
    ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1", CuentaCorriente.class);
    reciboDTO = restTemplate.getForObject(apiPrefix + "/recibos/2", ReciboDTO.class);
    assertEquals(reciboDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
    this.crearReciboParaProveedor(200);
    ccCliente =
      restTemplate.getForObject(
        apiPrefix + "/cuentas-corriente/proveedores/1", CuentaCorriente.class);
    reciboDTO = restTemplate.getForObject(apiPrefix + "/recibos/3", ReciboDTO.class);
    assertEquals(reciboDTO.getFecha(), ccCliente.getFechaUltimoMovimiento());
  }

  @Test
  void shouldGetMultiplesProductosPorIdEnOrden() {
    this.shouldCrearPedido();
    List<Long> idsProductos = new ArrayList<>();
    idsProductos.add(1L);
    idsProductos.add(2L);
    List<Producto> productos = productoService.getMultiplesProductosPorId(idsProductos);
    List<RenglonPedido> renglones = pedidoService.getRenglonesDelPedido(1L);
    assertEquals(productos.get(0).getIdProducto(), renglones.get(0).getIdProductoItem());
    assertEquals(productos.get(1).getIdProducto(), renglones.get(1).getIdProductoItem());
  }

  @Test
  void shouldVerificarTotalizadoresVenta() {
    BigDecimal totalFacturadoVenta =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-facturado-venta/criteria?idEmpresa=1", BigDecimal.class);
    BigDecimal totalIvaVenta =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-iva-venta/criteria?idEmpresa=1", BigDecimal.class);
    BigDecimal gananciaTotal =
      restTemplate.getForObject(
        apiPrefix + "/facturas/ganancia-total/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(BigDecimal.ZERO, totalFacturadoVenta);
    assertEquals(BigDecimal.ZERO, totalIvaVenta);
    assertEquals(BigDecimal.ZERO, gananciaTotal);
    this.shouldCrearFacturaVentaA();
    totalFacturadoVenta =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-facturado-venta/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("8230.762500000000000"), totalFacturadoVenta);
    totalIvaVenta =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-iva-venta/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("1218.262500000000000"), totalIvaVenta);
    gananciaTotal =
      restTemplate.getForObject(
        apiPrefix + "/facturas/ganancia-total/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("8100.000000000000000000000000000000"), gananciaTotal);
    ProductoDTO producto1 =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    producto1.setCantidad(BigDecimal.TEN);
    restTemplate.put(apiPrefix + "/productos", producto1);
    ProductoDTO producto2 =
      restTemplate.getForObject(apiPrefix + "/productos/1", ProductoDTO.class);
    producto2.setCantidad(new BigDecimal("6"));
    restTemplate.put(apiPrefix + "/productos", producto2);
    this.shouldCrearFacturaVentaPresupuesto();
    totalFacturadoVenta =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-facturado-venta/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("14223.262500000000000"), totalFacturadoVenta);
    totalIvaVenta =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-iva-venta/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("1218.262500000000000"), totalIvaVenta);
    gananciaTotal =
      restTemplate.getForObject(
        apiPrefix + "/facturas/ganancia-total/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("14400.000000000000000000000000000000"), gananciaTotal);
  }

  @Test
  void shouldVerificarTotalizadoresCompra() {
    BigDecimal totalFacturadoCompra =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-facturado-venta/criteria?idEmpresa=1", BigDecimal.class);
    BigDecimal totalIvaCompra =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-iva-compra/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(BigDecimal.ZERO, totalFacturadoCompra);
    assertEquals(BigDecimal.ZERO, totalIvaCompra);
    this.shouldCrearFacturaCompraA();
    totalFacturadoCompra =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-facturado-compra/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("610.895000000000000"), totalFacturadoCompra);
    totalIvaCompra =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-iva-compra/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("83.895000000000000"), totalIvaCompra);
    this.shouldCrearFacturaCompraPresupuesto();
    totalFacturadoCompra =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-facturado-compra/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("1210.145000000000000"), totalFacturadoCompra);
    totalIvaCompra =
      restTemplate.getForObject(
        apiPrefix + "/facturas/total-iva-compra/criteria?idEmpresa=1", BigDecimal.class);
    assertEquals(new BigDecimal("83.895000000000000"), totalIvaCompra);
  }

  @Test
  void shouldVerificarSaldoCaja() {
    CajaDTO caja =
      restTemplate.postForObject(
        apiPrefix + "/cajas/apertura/empresas/1?saldoApertura=200",
        null,
        CajaDTO.class);
    assertEquals(new BigDecimal("200"), caja.getSaldoApertura());
    this.crearReciboParaCliente(300);
    assertEquals(
      new BigDecimal("500.000000000000000"),
      restTemplate.getForObject(apiPrefix + "/cajas/1/saldo-sistema", BigDecimal.class));
    this.crearReciboParaProveedor(500);
    assertEquals(
      new BigDecimal("0E-15"),
      restTemplate.getForObject(apiPrefix + "/cajas/1/saldo-sistema", BigDecimal.class));
    GastoDTO gasto =
      GastoDTO.builder()
        .concepto("Gasto test")
        .fecha(new Date())
        .monto(new BigDecimal("200"))
        .build();
    restTemplate.postForObject(
      apiPrefix + "/gastos?idEmpresa=1&idFormaDePago=1", gasto, GastoDTO.class);
    assertEquals(
      new BigDecimal("-200.000000000000000"),
      restTemplate.getForObject(apiPrefix + "/cajas/1/saldo-sistema", BigDecimal.class));
  }

  @Test
  void shouldModificarCostoEnvioDeLocalidad() {
    LocalidadDTO localidad = restTemplate.getForObject(apiPrefix + "/ubicaciones/localidades/1", LocalidadDTO.class);
    localidad.setEnvioGratuito(true);
    localidad.setCostoEnvio(new BigDecimal("450"));
    restTemplate.put(apiPrefix + "/ubicaciones/localidades", localidad);
    assertTrue(localidad.isEnvioGratuito());
    assertEquals(new BigDecimal("450"), localidad.getCostoEnvio());
  }

  @Test
  void shouldVerificarFechaCierreCajaSinScheduling() {
    restTemplate.postForObject(apiPrefix + "/cajas/apertura/empresas/1?saldoApertura=200", null, CajaDTO.class);
    clockService.cambiarFechaHora(2030, 9, 24, 23, 59, 59);
    restTemplate.put(apiPrefix + "/cajas/1/cierre?monto=300", CajaDTO.class);
    CajaDTO cajaRecuperada = restTemplate.getForObject(apiPrefix + "/cajas/1", CajaDTO.class);
    Calendar fechaCierre = Calendar.getInstance();
    fechaCierre.setTime(cajaRecuperada.getFechaCierre());
    assertEquals(2030, fechaCierre.get(Calendar.YEAR));
    assertEquals(8, fechaCierre.get(Calendar.MONTH));
    assertEquals(24, fechaCierre.get(Calendar.DATE));
  }

  @Test
  void shouldVerificarFechaCierreCajaConScheduling() {
    clockService.cambiarFechaHora(2019, 12, 31, 10, 15, 35);
    restTemplate.postForObject(apiPrefix + "/cajas/apertura/empresas/1?saldoApertura=200", null, CajaDTO.class);
    cajaService.cerrarCaja(1L, new BigDecimal("300"), 1L, true);
    CajaDTO cajaRecuperada = restTemplate.getForObject(apiPrefix + "/cajas/1", CajaDTO.class);
    Calendar fechaCierre = Calendar.getInstance();
    fechaCierre.setTime(cajaRecuperada.getFechaCierre());
    assertEquals(2019, fechaCierre.get(Calendar.YEAR));
    assertEquals(11, fechaCierre.get(Calendar.MONTH));
    assertEquals(31, fechaCierre.get(Calendar.DATE));
    assertEquals(23, fechaCierre.get(Calendar.HOUR_OF_DAY));
    assertEquals(59, fechaCierre.get(Calendar.MINUTE));
    assertEquals(59, fechaCierre.get(Calendar.SECOND));
  }
}
