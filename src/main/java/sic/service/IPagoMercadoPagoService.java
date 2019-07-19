package sic.service;

import sic.modelo.Usuario;
import sic.modelo.dto.NuevoPagoMercadoPagoDTO;
import sic.modelo.dto.PagoMercadoPagoDTO;

public interface IPagoMercadoPagoService {

  void crearNuevoPago(NuevoPagoMercadoPagoDTO nuevoPagoMercadoPagoDTO, Usuario usuario);

  void crearReciboPorNotificacion(String idPayment);

  PagoMercadoPagoDTO recuperarPago(String idPayment);

  NuevoPagoMercadoPagoDTO devolverPago(String idPago, Usuario usuario);
}
