package sic.controller;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sic.modelo.Factura;
import sic.modelo.Nota;
import sic.modelo.NotaCredito;
import sic.modelo.NotaDebito;
import sic.modelo.RenglonNotaCredito;
import sic.modelo.RenglonNotaDebito;
import sic.modelo.TipoDeComprobante;
import sic.service.IClienteService;
import sic.service.IEmpresaService;
import sic.service.INotaService;
import sic.service.IReciboService;

@RestController
@RequestMapping("/api/v1")
public class NotaController {
    
    private final INotaService notaService;
    private final IReciboService reciboService;
    
    @Autowired
    public NotaController(INotaService notaService, IClienteService clienteService,
            IEmpresaService empresaService, IReciboService reciboService) {
        this.notaService = notaService;
        this.reciboService = reciboService;
    }
    
    @GetMapping("/notas/{idNota}")
    @ResponseStatus(HttpStatus.OK)
    public Nota getNota(@PathVariable long idNota) {
        return notaService.getNotaPorId(idNota);
    }
 
    @GetMapping("/notas/{idNota}/facturas")
    @ResponseStatus(HttpStatus.OK)
    public Factura getFacturaNota(@PathVariable long idNota) {
        return notaService.getFacturaNotaCredito(idNota);
    }
    
    @GetMapping("/notas/debito/recibo/{idRecibo}/existe")
    @ResponseStatus(HttpStatus.OK)
    public boolean existeNotaDebitoRecibo(@PathVariable long idRecibo) {
        return notaService.existeNotaDebitoPorRecibo(reciboService.getById(idRecibo));
    }
    
    @GetMapping("/notas/cliente/{idCliente}/empresa/{idEmpresa}")
    @ResponseStatus(HttpStatus.OK)
    public List<Nota> getNotasPorClienteYEmpresa(Long idEmpresa, Long idCliente) {
        return notaService.getNotasCreditoPorClienteYEmpresa(idEmpresa, idCliente);
    }
    
    @GetMapping("/notas/pagos/{idPago}")
    @ResponseStatus(HttpStatus.OK)
    public Nota getNotaDelPago(@PathVariable long idPago) {
        return notaService.getNotaDelPago(idPago);
    }
    
    @GetMapping("/notas/tipos")
    @ResponseStatus(HttpStatus.OK)
    public TipoDeComprobante[] getTipoNota(@RequestParam long idCliente,
                                           @RequestParam long idEmpresa) {
        return notaService.getTipoNotaCliente(idCliente, idEmpresa);
    }
    
    @GetMapping("/notas/renglones/credito/{idNotaCredito}")
    @ResponseStatus(HttpStatus.OK)
    public List<RenglonNotaCredito> getRenglonesDeNotaCredito(@RequestParam long idNotaCredito) {
        return notaService.getRenglonesDeNotaCredito(idNotaCredito);
    }
    
    @GetMapping("/notas/renglones/debito/{idNotaDebito}")
    @ResponseStatus(HttpStatus.OK)
    public List<RenglonNotaDebito> getRenglonesDeNotaDebito(@RequestParam long idNotaDebito) {
        return notaService.getRenglonesDeNotaDebito(idNotaDebito);
    }
    
    @PostMapping("/notas/credito/empresa/{idEmpresa}/cliente/{idCliente}/usuario/{idUsuario}/factura/{idFactura}")
    @ResponseStatus(HttpStatus.CREATED)
    public Nota guardarNotaCredito(@RequestBody NotaCredito nota,
                                   @PathVariable long idEmpresa,
                                   @PathVariable long idCliente,
                                   @PathVariable long idUsuario,
                                   @PathVariable long idFactura, 
                                   @RequestParam boolean modificarStock) {
        return notaService.guardarNota(nota, idEmpresa, idCliente, idUsuario, null, idFactura, modificarStock);
    }
    
    @PostMapping("/notas/debito/empresa/{idEmpresa}/cliente/{idCliente}/usuario/{idUsuario}/recibo/{idRecibo}")
    @ResponseStatus(HttpStatus.CREATED)
    public Nota guardarNotaDebito(@RequestBody NotaDebito nota,
                                  @PathVariable long idEmpresa,
                                  @PathVariable long idCliente,
                                  @PathVariable long idUsuario,
                                  @PathVariable long idRecibo) {
        return notaService.guardarNota(nota, idEmpresa, idCliente, idUsuario, idRecibo, null, false);
    }

    @GetMapping("/notas/{idNota}/reporte")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<byte[]> getReporteNota(@PathVariable long idNota) {        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);   
        Nota nota = notaService.getNotaPorId(idNota);
        String fileName = (nota instanceof NotaCredito) ? "NotaCredito.pdf" : (nota instanceof NotaDebito) ? "NotaDebito.pdf" : "Nota.pdf";
        headers.add("content-disposition", "inline; filename=" + fileName);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        byte[] reportePDF = notaService.getReporteNota(nota);
        return new ResponseEntity<>(reportePDF, headers, HttpStatus.OK);
    }
    
    @PostMapping("/notas/{idNota}/autorizacion")
    @ResponseStatus(HttpStatus.CREATED)
    public Nota autorizarNota(@PathVariable long idNota) {
        return notaService.autorizarNota(notaService.getNotaPorId(idNota));
    }
    
    @DeleteMapping("/notas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarNota(@RequestParam long[] idsNota) {
        notaService.eliminarNota(idsNota);
    }
    
    @GetMapping("/notas/{idNota}/iva-neto")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal getIvaNetoNota(@PathVariable long idNota) {
        return notaService.getIvaNetoNota(idNota);
    }
    
    @GetMapping("/notas/renglon/credito/producto") 
    @ResponseStatus(HttpStatus.OK) 
    public List<RenglonNotaCredito> calcularRenglonNotaCreditoProducto(@RequestParam TipoDeComprobante tipoDeComprobante,
                                                                       @RequestParam BigDecimal[] cantidad,
                                                                       @RequestParam long[] idRenglonFactura) {
        return notaService.calcularRenglonCredito(tipoDeComprobante, cantidad, idRenglonFactura);
    }
    
    @GetMapping("/notas/renglon/debito/recibo/{idRecibo}")
    @ResponseStatus(HttpStatus.OK) 
    public List<RenglonNotaDebito> calcularRenglonNotaDebito(@PathVariable long idRecibo, 
                                                             @RequestParam BigDecimal monto,
                                                             @RequestParam BigDecimal ivaPorcentaje) {
        return notaService.calcularRenglonDebito(idRecibo, monto, ivaPorcentaje);
    }
    
    @GetMapping("/notas/credito/sub-total")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal calcularSubTotalCredito(@RequestParam BigDecimal[] importe) {
        return notaService.calcularSubTotalCredito(importe);
    }
    
    @GetMapping("/notas/credito/descuento-neto")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal calcularDescuentoNetoCredito(@RequestParam BigDecimal subTotal,
                                                   @RequestParam BigDecimal descuentoPorcentaje) {
        return notaService.calcularDecuentoNetoCredito(subTotal, descuentoPorcentaje);
    }
    
    @GetMapping("/notas/credito/recargo-neto")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal calcularRecargoNetoCredito(@RequestParam BigDecimal subTotal,
                                                 @RequestParam BigDecimal recargoPorcentaje) {
        return notaService.calcularRecargoNetoCredito(subTotal, recargoPorcentaje);
    }
    
    @GetMapping("/notas/credito/iva-neto")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal calcularIVANetoCredito(@RequestParam TipoDeComprobante tipoDeComprobante,
                                             @RequestParam BigDecimal[] cantidades,
                                             @RequestParam BigDecimal[] ivaPorcentajeRenglones,
                                             @RequestParam BigDecimal[] ivaNetoRenglones,
                                             @RequestParam BigDecimal ivaPorcentaje,
                                             @RequestParam BigDecimal descuentoPorcentaje, 
                                             @RequestParam BigDecimal recargoPorcentaje){
        return notaService.calcularIVANetoCredito(tipoDeComprobante, cantidades, ivaPorcentajeRenglones, ivaNetoRenglones, ivaPorcentaje, descuentoPorcentaje, recargoPorcentaje);
    }  
    
    @GetMapping("/notas/credito/sub-total-bruto")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal calcularSubTotalBrutoCredito(TipoDeComprobante tipoDeComprobante, 
                                                   BigDecimal subTotal, 
                                                   BigDecimal recargoNeto, 
                                                   BigDecimal descuentoNeto,
                                                   BigDecimal iva105Neto,
                                                   BigDecimal iva21Neto) {
        return notaService.calcularSubTotalBrutoCredito(tipoDeComprobante, subTotal, recargoNeto, descuentoNeto, iva105Neto, iva21Neto);
    }
    
    @GetMapping("/notas/credito/total")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal calcularTotalCredito(@RequestParam BigDecimal subTotalBruto,                                
                                           @RequestParam BigDecimal iva105Neto,
                                           @RequestParam BigDecimal iva21Neto) {
        return notaService.calcularTotalCredito(subTotalBruto, iva105Neto, iva21Neto);
    }
    
    @GetMapping("/notas/debito/total")
    @ResponseStatus(HttpStatus.OK)
    public BigDecimal calcularTotalDebito(BigDecimal subTotalBruto,                                
                                          BigDecimal iva21Neto,
                                          BigDecimal montoNoGravado) {
        return notaService.calcularTotalDebito(subTotalBruto, iva21Neto, montoNoGravado);
    }
    
}
