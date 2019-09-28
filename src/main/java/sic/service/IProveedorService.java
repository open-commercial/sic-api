package sic.service;

import java.util.List;

import org.springframework.data.domain.Page;
import sic.modelo.criteria.BusquedaProveedorCriteria;
import sic.modelo.Empresa;
import sic.modelo.Proveedor;

import javax.validation.Valid;

public interface IProveedorService {

  Proveedor getProveedorNoEliminadoPorId(long idProveedor);

  void actualizar(@Valid Proveedor proveedor);

  Page<Proveedor> buscarProveedores(BusquedaProveedorCriteria criteria);

  void eliminar(long idProveedor);

  Proveedor getProveedorPorIdFiscal(Long idFiscal, Empresa empresa);

  Proveedor getProveedorPorRazonSocial(String razonSocial, Empresa empresa);

  List<Proveedor> getProveedores(Empresa empresa);

  Proveedor guardar(@Valid Proveedor proveedor);

  String generarNroDeProveedor(Empresa empresa);
}
