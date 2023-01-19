package sic.service;

import io.jsonwebtoken.Claims;
import sic.modelo.Rol;
import sic.modelo.Usuario;

import java.util.List;

public interface IAuthService {

  String generarJWT(long idUsuario, List<Rol> rolesDeUsuario);

  boolean esAuthorizationHeaderValido(String authorizationHeader);

  boolean esJWTValido(String token);

  boolean noEsTokenExcluido(String token);

  Claims getClaimsDelToken(String authorizationHeader);

  void validarRecaptcha(String recaptcha);

  void excluirTokenAcceso(String authorizationHeader);

  void setActiveUserToken(String token);

  long getActiveUserId();
}
