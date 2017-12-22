package sic.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sic.modelo.Recibo;
import sic.service.IClienteService;
import sic.service.IEmpresaService;
import sic.service.IFormaDePagoService;
import sic.service.IReciboService;
import sic.service.IUsuarioService;

@RestController
@RequestMapping("/api/v1")
public class ReciboController {
    
    private final IReciboService reciboService;
    private final IEmpresaService empresaService;
    private final IUsuarioService usuarioService;
    private final IClienteService clienteService;
    private final IFormaDePagoService formaDePagoService;
    
    @Autowired
    public ReciboController(IReciboService reciboService, IEmpresaService empresaService,
                            IUsuarioService usuarioService, IClienteService clienteService,
                            IFormaDePagoService formaDePagoService) {
        this.reciboService = reciboService;
        this.empresaService = empresaService;
        this.usuarioService = usuarioService;
        this.clienteService = clienteService;
        this.formaDePagoService = formaDePagoService;
    }
    
    @GetMapping("/recibos/{idRecibo}")
    @ResponseStatus(HttpStatus.OK)
    public Recibo getReciboPorId(@PathVariable long idRecibo) {
        return reciboService.getById(idRecibo);
    }
    
    @PostMapping("/recibos")
    @ResponseStatus(HttpStatus.CREATED)
    public Recibo guardar(@RequestParam long idUsuario,
                          @RequestParam long idEmpresa,
                          @RequestParam long idCliente,
                          @RequestParam long idFormaDePago,
                          @RequestBody Recibo recibo) {
        recibo.setEmpresa(empresaService.getEmpresaPorId(idEmpresa));
        recibo.setUsuario(usuarioService.getUsuarioPorId(idUsuario));
        recibo.setCliente(clienteService.getClientePorId(idCliente));
        recibo.setFormaDePago(formaDePagoService.getFormasDePagoPorId(idFormaDePago));
        return reciboService.guardar(recibo);
    }
    
    @DeleteMapping("/recibos/{idRecibo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable long idRecibo) {
        reciboService.eliminar(idRecibo);
    }
    
}
