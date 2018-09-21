package sic.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sic.modelo.*;
import sic.modelo.dto.RecoveryPasswordDTO;
import sic.modelo.dto.RegistracionClienteAndUsuarioDTO;
import sic.service.IEmpresaService;
import sic.service.IRegistracionService;
import sic.service.IUsuarioService;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

  private final IUsuarioService usuarioService;
  private final IEmpresaService empresaService;
  private final IRegistracionService registracionService;

  @Value("${SIC_JWT_KEY}")
  private String secretkey;

  @Autowired
  public AuthController(IUsuarioService usuarioService,
                        IEmpresaService empresaService,
                        IRegistracionService registracionService) {
    this.usuarioService = usuarioService;
    this.empresaService = empresaService;
    this.registracionService = registracionService;
  }

  private String generarToken(long idUsuario, List<Rol> rolesDeUsuario) {
    // 24hs desde la fecha actual para expiration
    Date today = new Date();
    Calendar c = Calendar.getInstance();
    c.setTime(today);
    c.add(Calendar.DATE, 1);
    Date tomorrow = c.getTime();
    return Jwts.builder()
        .setIssuedAt(today)
        .setExpiration(tomorrow)
        .signWith(SignatureAlgorithm.HS512, secretkey)
        .claim("idUsuario", idUsuario)
        .claim("roles", rolesDeUsuario)
        .compact();
  }

  @PostMapping("/login")
  public String login(@RequestBody Credencial credencial) {
    Usuario usuario = usuarioService.autenticarUsuario(credencial);
    String token = this.generarToken(usuario.getId_Usuario(), usuario.getRoles());
    usuarioService.actualizarToken(token, usuario.getId_Usuario());
    return token;
  }

  @PutMapping("/logout")
  public void logout(@RequestHeader("Authorization") String token) {
    Claims claims;
    try {
      claims = Jwts.parser().setSigningKey(secretkey).parseClaimsJws(token.substring(7)).getBody();
    } catch (JwtException ex) {
      throw new UnauthorizedException(
          ResourceBundle.getBundle("Mensajes").getString("mensaje_error_token_vacio_invalido"), ex);
    }
    long idUsuario = (int) claims.get("idUsuario");
    usuarioService.actualizarToken("", idUsuario);
  }

  @GetMapping("/password-recovery")
  @ResponseStatus(HttpStatus.OK)
  public void recuperarPassword(
      @RequestParam String email, @RequestParam long idEmpresa, HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin == null) origin = request.getHeader("Host");
    usuarioService.enviarEmailDeRecuperacion(idEmpresa, email, origin);
  }

  @PostMapping("/password-recovery")
  public String generarTokenTemporal(@RequestBody RecoveryPasswordDTO recoveryPasswordDTO) {
    String token;
    Usuario usuario =
        usuarioService.getUsuarioPorPasswordRecoveryKeyAndIdUsuario(
            recoveryPasswordDTO.getKey(), recoveryPasswordDTO.getId());
    if (usuario != null && (new Date()).before(usuario.getPasswordRecoveryKeyExpirationDate())) {
      token = this.generarToken(usuario.getId_Usuario(), usuario.getRoles());
      usuarioService.actualizarToken(token, usuario.getId_Usuario());
      usuarioService.actualizarPasswordRecoveryKey(null, recoveryPasswordDTO.getId());
    } else {
      throw new UnauthorizedException(
          ResourceBundle.getBundle("Mensajes").getString("mensaje_error_passwordRecoveryKey"));
    }
    return token;
  }

  @PostMapping("/registracion")
  public void registrarse(
      @RequestBody RegistracionClienteAndUsuarioDTO registracionClienteAndUsuarioDTO) {
    Usuario nuevoUsuario = new Usuario();
    nuevoUsuario.setHabilitado(false);
    nuevoUsuario.setNombre(registracionClienteAndUsuarioDTO.getNombre());
    nuevoUsuario.setApellido(registracionClienteAndUsuarioDTO.getApellido());
    nuevoUsuario.setEmail(registracionClienteAndUsuarioDTO.getEmail());
    nuevoUsuario.setPassword(registracionClienteAndUsuarioDTO.getPassword());
    nuevoUsuario.setRoles(Collections.singletonList(Rol.COMPRADOR));
    Cliente nuevoCliente = new Cliente();
    nuevoCliente.setTipoDeCliente(registracionClienteAndUsuarioDTO.getTipoDeCliente());
    nuevoCliente.setEmail(registracionClienteAndUsuarioDTO.getEmail());
    nuevoCliente.setEmpresa(empresaService.getEmpresaPorId(registracionClienteAndUsuarioDTO.getIdEmpresa()));
    if (nuevoCliente.getTipoDeCliente() == TipoDeCliente.EMPRESA) {
      nuevoCliente.setRazonSocial(registracionClienteAndUsuarioDTO.getRazonSocial());
      nuevoCliente.setCategoriaIVA(CategoriaIVA.RESPONSABLE_INSCRIPTO);
    } else if (nuevoCliente.getTipoDeCliente() == TipoDeCliente.PERSONA) {
      nuevoCliente.setRazonSocial(
          registracionClienteAndUsuarioDTO.getNombre()
              + " "
              + registracionClienteAndUsuarioDTO.getApellido());
      nuevoCliente.setCategoriaIVA(CategoriaIVA.CONSUMIDOR_FINAL);
    }
    this.registracionService.crearCuentaConClienteAndUsuario(nuevoCliente, nuevoUsuario);
  }
}
