package sic.controller;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import sic.aspect.AccesoRolesPermitidos;
import sic.modelo.Rol;
import sic.modelo.Rubro;
import sic.modelo.dto.RubroDTO;
import sic.service.IRubroService;

@RestController
@RequestMapping("/api/v1")
public class RubroController {

  private final IRubroService rubroService;
  private final ModelMapper modelMapper;

  @Autowired
  public RubroController(
      IRubroService rubroService, ModelMapper modelMapper) {
    this.rubroService = rubroService;
    this.modelMapper = modelMapper;
  }

  @GetMapping("/rubros/{idRubro}")
  @AccesoRolesPermitidos({Rol.ADMINISTRADOR, Rol.ENCARGADO})
  public Rubro getRubroPorId(@PathVariable long idRubro) {
    return rubroService.getRubroNoEliminadoPorId(idRubro);
  }

  @PutMapping("/rubros")
  @AccesoRolesPermitidos({Rol.ADMINISTRADOR, Rol.ENCARGADO})
  public void actualizar(
      @RequestBody RubroDTO rubroDTO, @RequestParam(required = false) Long idEmpresa) {
    Rubro rubroPersistido = rubroService.getRubroNoEliminadoPorId(rubroDTO.getId_Rubro());
    Rubro rubroPorActualizar = modelMapper.map(rubroDTO, Rubro.class);
    if (rubroPorActualizar.getNombre() == null || rubroPorActualizar.getNombre().isEmpty()) {
      rubroPorActualizar.setNombre(rubroPersistido.getNombre());
    }
    rubroService.actualizar(rubroPorActualizar);
  }

  @DeleteMapping("/rubros/{idRubro}")
  @AccesoRolesPermitidos(Rol.ADMINISTRADOR)
  public void eliminar(@PathVariable long idRubro) {
    rubroService.eliminar(idRubro);
  }

  @PostMapping("/rubros")
  @AccesoRolesPermitidos({Rol.ADMINISTRADOR, Rol.ENCARGADO})
  public Rubro guardar(@RequestBody RubroDTO rubroDTO) {
    Rubro rubro = modelMapper.map(rubroDTO, Rubro.class);
    return rubroService.guardar(rubro);
  }

  @GetMapping("/rubros")
  @AccesoRolesPermitidos({
    Rol.ADMINISTRADOR,
    Rol.ENCARGADO,
    Rol.VENDEDOR,
    Rol.VIAJANTE,
    Rol.COMPRADOR
  })
  public List<Rubro> getRubros() {
    return rubroService.getRubros();
  }
}
