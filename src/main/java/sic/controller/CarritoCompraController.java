package sic.controller;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import sic.modelo.ItemCarritoCompra;
import sic.modelo.Pedido;
import sic.modelo.RenglonPedido;
import sic.service.ICarritoCompraService;
import sic.service.IPedidoService;

@RestController
@RequestMapping("/api/v1")
public class CarritoCompraController {

    private final int TAMANIO_PAGINA_DEFAULT = 10;
    private final ICarritoCompraService carritoCompraService;
    private final IPedidoService pedidoService;

    @Autowired
    public CarritoCompraController(ICarritoCompraService carritoCompraService,
                                   IPedidoService pedidoService) {
        this.carritoCompraService = carritoCompraService;
        this.pedidoService = pedidoService;
    }

    @GetMapping("/carrito-compra/usuarios/{idUsuario}")
    @ResponseStatus(HttpStatus.OK)
    public Page<ItemCarritoCompra> getAllItemsDelUsuario(@PathVariable long idUsuario,
                                                         @RequestParam(required = false) Integer pagina,
                                                         @RequestParam(required = false) Integer tamanio) {
        if (tamanio == null || tamanio <= 0) {
            tamanio = TAMANIO_PAGINA_DEFAULT;
        }
        if (pagina == null || pagina < 0) {
            pagina = 0;
        }
        Pageable pageable = new PageRequest(pagina, tamanio, new Sort(Sort.Direction.DESC, "idItemCarritoCompra"));
        return carritoCompraService.getAllItemsDelUsuario(idUsuario, pageable);
    }

    @GetMapping("/carrito-compra/usuarios/{idUsuario}/total")
    @ResponseStatus(HttpStatus.OK)
    public double getTotal(@PathVariable long idUsuario) {
        return carritoCompraService.getTotal(idUsuario);
    }

    @GetMapping("/carrito-compra/usuarios/{idUsuario}/cantidad-articulos")
    @ResponseStatus(HttpStatus.OK)
    public double getCantArticulos(@PathVariable long idUsuario) {
        return carritoCompraService.getCantArticulos(idUsuario);
    }    

    @DeleteMapping("/carrito-compra/usuarios/{idUsuario}/productos/{idProducto}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarItem(@PathVariable long idUsuario, @PathVariable long idProducto) {
        carritoCompraService.eliminarItem(idUsuario, idProducto);
    }

    @DeleteMapping("/carrito-compra/usuarios/{idUsuario}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTodosLosItems(@PathVariable long idUsuario) {
        carritoCompraService.eliminarTodosLosItems(idUsuario);
    }    
    
    @PostMapping("/carrito-compra/usuarios/{idUsuario}/productos/{idProducto}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void agregarOrModificarItem(@PathVariable long idUsuario, @PathVariable long idProducto, @RequestParam double cantidad) {
        carritoCompraService.agregarOrModificarItem(idUsuario, idProducto, cantidad);
    }

    @GetMapping("/carrito-compra/usuarios/{idUsuario}/cantidad-renglones")
    @ResponseStatus(HttpStatus.OK)
    public long getCantRenglones(@PathVariable long idUsuario) {
        return carritoCompraService.getCantRenglones(idUsuario);
    }

    @PostMapping("/carrito-compra")
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido generarPedidoConItemsDelCarrito(@RequestBody Pedido pedido) {
        Pageable pageable = new PageRequest(0, Integer.MAX_VALUE, new Sort(Sort.Direction.DESC, "idItemCarritoCompra"));
        List<ItemCarritoCompra> items = carritoCompraService.getAllItemsDelUsuario(pedido.getUsuario().getId_Usuario(), pageable).getContent();
        pedido.setRenglones(new ArrayList<>());
        items.forEach(i -> {
            pedido.getRenglones().add(new RenglonPedido(0, i.getProducto(), i.getCantidad(), 0, 0, i.getImporte()));
        });
        Pedido p = pedidoService.guardar(pedido);
        carritoCompraService.eliminarTodosLosItems(pedido.getUsuario().getId_Usuario());
        return p;
    }
}
