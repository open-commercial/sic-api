package sic.service.impl;

import com.mercadopago.MercadoPago;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.Payment;
import com.mercadopago.resources.datastructures.payment.Payer;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sic.exception.BusinessServiceException;
import sic.modelo.Cliente;
import sic.modelo.Recibo;
import sic.modelo.Usuario;
import sic.modelo.dto.NuevaNotaDebitoDeReciboDTO;
import sic.modelo.dto.NuevoPagoMercadoPagoDTO;
import sic.modelo.dto.PagoMercadoPagoDTO;
import sic.service.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

@Service
public class MercadoPagoServiceImpl implements IPagoMercadoPagoService {

  @Value("${SIC_MERCADOPAGO_ACCESS_TOKEN}")
  private String mercadoPagoAccesToken;

  private static final String[] pagosEnEfectivoPermitidos =
      new String[] {"pagofacil", "rapipago", "cobroexpress", "cargavirtual"};
  private final IReciboService reciboService;
  private final IFormaDePagoService formaDePagoService;
  private final IClienteService clienteService;
  private final INotaService notaService;
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final ModelMapper modelMapper;
  private final MessageSource messageSource;

  @Autowired
  public MercadoPagoServiceImpl(
      IReciboService reciboService,
      IFormaDePagoService formaDePagoService,
      IClienteService clienteService,
      INotaService notaService,
      ModelMapper modelMapper,
      MessageSource messageSource) {
    this.reciboService = reciboService;
    this.formaDePagoService = formaDePagoService;
    this.clienteService = clienteService;
    this.notaService = notaService;
    this.modelMapper = modelMapper;
    this.messageSource = messageSource;
  }

  @Override
  public void crearNuevoPago(NuevoPagoMercadoPagoDTO nuevoPagoMercadoPagoDTO, Usuario usuario) {
    Cliente cliente =
        clienteService.getClienteNoEliminadoPorId(nuevoPagoMercadoPagoDTO.getIdCliente());
    this.validarOperacion(nuevoPagoMercadoPagoDTO, cliente);
    MercadoPago.SDK.configure(mercadoPagoAccesToken);
    Payment payment = new Payment();
    payment.setDescription(
        "("
            + cliente.getNroCliente()
            + ")"
            + " "
            + cliente.getNombreFiscal()
            + (cliente.getNombreFantasia() != null ? cliente.getNombreFantasia() : ""));
    payment.setExternalReference(String.valueOf(cliente.getId_Cliente()));
    Payer payer = new Payer();
    payer.setEmail(cliente.getEmail());
    payment.setPayer(payer);
    if (nuevoPagoMercadoPagoDTO.getToken() != null
        && !nuevoPagoMercadoPagoDTO.getToken().isEmpty()) {
      payment
          .setTransactionAmount(nuevoPagoMercadoPagoDTO.getMonto())
          .setToken(nuevoPagoMercadoPagoDTO.getToken())
          .setInstallments(nuevoPagoMercadoPagoDTO.getInstallments())
          .setIssuerId(nuevoPagoMercadoPagoDTO.getIssuerId())
          .setPaymentMethodId(nuevoPagoMercadoPagoDTO.getPaymentMethodId())
          .setBinaryMode(true);
    } else if (Arrays.asList(pagosEnEfectivoPermitidos)
        .contains(nuevoPagoMercadoPagoDTO.getPaymentMethodId())) {
      payment
          .setTransactionAmount(nuevoPagoMercadoPagoDTO.getMonto())
          .setPaymentMethodId(nuevoPagoMercadoPagoDTO.getPaymentMethodId());
    } else {
      throw new BusinessServiceException(
          messageSource.getMessage("mensaje_pago_no_soportado", null, Locale.getDefault()));
    }
    try {
      Payment p = payment.save();
      if (p.getStatus() == Payment.Status.rejected) {
        this.procesarMensajeNoAprobado(payment);
      }
    } catch (MPException ex) {
      this.logExceptionMercadoPago(ex);
    }
  }

  @Override
  @Async
  public void crearComprobantePorNotificacion(String idPayment) {
    Payment payment;
    try {
      MercadoPago.SDK.configure(mercadoPagoAccesToken);
      payment = Payment.findById(idPayment);
      if (payment.getId() != null && payment.getExternalReference() != null) {
        Cliente cliente =
            clienteService.getClienteNoEliminadoPorId(Long.valueOf(payment.getExternalReference()));
        switch (payment.getStatus()) {
          case approved:
            if (reciboService.getReciboPorIdMercadoPago(idPayment) == null) {
              this.crearReciboDePago(payment, cliente.getCredencial(), cliente);
            } else {
              logger.warn("El recibo del pago nro {} ya existe.", payment.getId());
            }
            break;
          case refunded:
            Recibo reciboDeMercadoPago = reciboService.getReciboPorIdMercadoPago(idPayment);
            if (!notaService.existsNotaDebitoPorRecibo(reciboDeMercadoPago)) {
              NuevaNotaDebitoDeReciboDTO nuevaNotaDebitoDeReciboDTO =
                  NuevaNotaDebitoDeReciboDTO.builder()
                      .idRecibo(reciboDeMercadoPago.getIdRecibo())
                      .gastoAdministrativo(BigDecimal.ZERO)
                      .motivo("Devolución de pago por MercadoPago")
                      .tipoDeComprobante(
                          notaService
                              .getTipoNotaDebitoCliente(
                                  reciboDeMercadoPago.getIdCliente(),
                                  reciboDeMercadoPago.getEmpresa().getId_Empresa())
                              .get(0))
                      .build();
              notaService.guardarNotaDebito(
                  notaService.calcularNotaDebitoConRecibo(
                      nuevaNotaDebitoDeReciboDTO, cliente.getCredencial()));
            } else {
              logger.warn("La nota del pago nro {} ya existe.", payment.getId());
            }
            break;
          default:
            logger.warn("El status del pago nro {} no es soportado.", payment.getId());
            messageSource.getMessage("mensaje_pago_no_soportado", null, Locale.getDefault());
        }
      }
    } catch (MPException ex) {
      this.logExceptionMercadoPago(ex);
    }
  }

  @Override
  public PagoMercadoPagoDTO recuperarPago(String idPago) {
    MercadoPago.SDK.configure(mercadoPagoAccesToken);
    PagoMercadoPagoDTO pagoRecuperado = PagoMercadoPagoDTO.builder().build();
    try {
      Payment pagoMP = Payment.findById(idPago);
      pagoRecuperado = modelMapper.map(pagoMP, PagoMercadoPagoDTO.class);
    } catch (MPException ex) {
      this.logExceptionMercadoPago(ex);
    }
    return pagoRecuperado;
  }

  private void crearReciboDePago(Payment payment, Usuario usuario, Cliente cliente) {
    switch (payment.getStatus()) {
      case approved:
        logger.warn("El pago de mercadopago {} se aprobó correctamente.", payment);
        Recibo nuevoRecibo = new Recibo();
        nuevoRecibo.setEmpresa(cliente.getEmpresa());
        nuevoRecibo.setFormaDePago(formaDePagoService.getFormaDePago(payment.getPaymentMethodId()));
        nuevoRecibo.setUsuario(usuario);
        nuevoRecibo.setCliente(cliente);
        nuevoRecibo.setFecha(new Date());
        nuevoRecibo.setConcepto("Pago en MercadoPago (" + payment.getPaymentMethodId() + ")");
        nuevoRecibo.setMonto(new BigDecimal(Float.toString(payment.getTransactionAmount())));
        nuevoRecibo.setIdPagoMercadoPago(payment.getId());
        reciboService.guardar(nuevoRecibo);
        break;
      case pending:
        if (payment.getStatusDetail().equals("pending_waiting_payment")) {
          logger.warn("El pago {} está pendiente", payment.getId());
        } else {
          logger.warn("El pago {} no fué aprobado", payment.getId());
          this.procesarMensajeNoAprobado(payment);
        }
        break;
      default:
        logger.warn("El pago {} no fué aprobado", payment.getId());
        this.procesarMensajeNoAprobado(payment);
    }
  }

  private void logExceptionMercadoPago(MPException ex) {
    logger.warn("Ocurrió un error con MercadoPago: {}", ex.getMessage());
    throw new BusinessServiceException(
      messageSource.getMessage("mensaje_pago_error", null, Locale.getDefault()));
  }

  private void procesarMensajeNoAprobado(Payment payment) {
    if (payment.getStatusDetail() != null) {
      switch (payment.getStatusDetail()) {
        case "cc_rejected_card_disabled":
        case "cc_rejected_insufficient_amount":
        case "cc_rejected_other_reason":
          throw new BusinessServiceException(
              messageSource.getMessage(
                  payment.getStatusDetail(),
                  new Object[] {payment.getPaymentMethodId()},
                  Locale.getDefault()));
        case "cc_rejected_call_for_authorize":
          throw new BusinessServiceException(
              messageSource.getMessage(
                  payment.getStatusDetail(),
                  new Object[] {payment.getPaymentMethodId(), payment.getTransactionAmount()},
                  Locale.getDefault()));
        case "cc_rejected_invalid_installments":
          throw new BusinessServiceException(
              messageSource.getMessage(
                  payment.getStatusDetail(),
                  new Object[] {payment.getPaymentMethodId(), payment.getInstallments()},
                  Locale.getDefault()));
        default:
          throw new BusinessServiceException(
              messageSource.getMessage(payment.getStatusDetail(), null, Locale.getDefault()));
      }
    } else {
      throw new BusinessServiceException(payment.getLastApiResponse().getStringResponse());
    }
  }

  private void validarOperacion(NuevoPagoMercadoPagoDTO nuevoPagoMercadoPagoDTO, Cliente cliente) {
    if (cliente.getEmail() == null || cliente.getEmail().isEmpty()) {
      throw new BusinessServiceException(
          messageSource.getMessage("mensaje_pago_cliente_sin_email", null, Locale.getDefault()));
    }
    if (nuevoPagoMercadoPagoDTO.getInstallments() == null) {
      nuevoPagoMercadoPagoDTO.setInstallments(1);
    }
    if (nuevoPagoMercadoPagoDTO.getPaymentMethodId() != null
        && nuevoPagoMercadoPagoDTO.getPaymentMethodId().isEmpty()) {
      throw new BusinessServiceException(
          messageSource.getMessage(
              "mensaje_pago_sin_payment_method_id", null, Locale.getDefault()));
    }
    if (nuevoPagoMercadoPagoDTO.getToken() != null
        && !nuevoPagoMercadoPagoDTO.getToken().isEmpty()
        && (nuevoPagoMercadoPagoDTO.getIssuerId() == null
            || nuevoPagoMercadoPagoDTO.getIssuerId().isEmpty())) {
      throw new BusinessServiceException(
          messageSource.getMessage("mensaje_pago_sin_issuer_id", null, Locale.getDefault()));
    }
  }

}