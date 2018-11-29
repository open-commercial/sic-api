package sic.service.impl;

import java.util.List;
import java.util.ResourceBundle;
import javax.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sic.modelo.ConfiguracionDelSistema;
import sic.modelo.Empresa;
import sic.service.IEmpresaService;
import sic.service.BusinessServiceException;
import sic.modelo.TipoDeOperacion;
import sic.service.IPhotoVideoUploader;
import sic.util.Validator;
import sic.repository.EmpresaRepository;
import sic.service.IConfiguracionDelSistemaService;

@Service
public class EmpresaServiceImpl implements IEmpresaService {

    private final EmpresaRepository empresaRepository;
    private final IConfiguracionDelSistemaService configuracionDelSistemaService;
    private final IPhotoVideoUploader photoVideoUploader;
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Autowired
    public EmpresaServiceImpl(EmpresaRepository empresaRepository,
                              IConfiguracionDelSistemaService configuracionDelSistemaService,
                              IPhotoVideoUploader photoVideoUploader) {

        this.empresaRepository = empresaRepository;
        this.configuracionDelSistemaService = configuracionDelSistemaService;
        this.photoVideoUploader = photoVideoUploader;
    }
    
    @Override
    public Empresa getEmpresaPorId(Long idEmpresa){
        Empresa empresa = empresaRepository.findById(idEmpresa);
        if (empresa == null) {
            throw new EntityNotFoundException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_empresa_no_existente"));
        }
        return empresa;
    }

    @Override
    public List<Empresa> getEmpresas() {
        return empresaRepository.findAllByAndEliminadaOrderByNombreAsc(false);
    }

    @Override
    public Empresa getEmpresaPorNombre(String nombre) {
        return empresaRepository.findByNombreIsAndEliminadaOrderByNombreAsc(nombre, false);
    }

    @Override
    public Empresa getEmpresaPorIdFiscal(Long idFiscal) {
        return empresaRepository.findByIdFiscalAndEliminada(idFiscal, false);
    }

    private void validarOperacion(TipoDeOperacion operacion, Empresa empresa) {
        //Entrada de Datos
        if (empresa.getEmail() != null && !empresa.getEmail().equals("")) {
            if (!Validator.esEmailValido(empresa.getEmail())) {
                throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                        .getString("mensaje_empresa_email_invalido"));
            }
        }
        //Requeridos
        if (Validator.esVacio(empresa.getNombre())) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_empresa_vacio_nombre"));
        }
        if (Validator.esVacio(empresa.getDireccion())) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_empresa_vacio_direccion"));
        }
        if (empresa.getLocalidad() == null) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_empresa_vacio_localidad"));
        }
        //Duplicados
        //Nombre
        Empresa empresaDuplicada = this.getEmpresaPorNombre(empresa.getNombre());
        if (operacion == TipoDeOperacion.ALTA && empresaDuplicada != null) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_empresa_duplicado_nombre"));
        }
        if (operacion == TipoDeOperacion.ACTUALIZACION) {
            if (empresaDuplicada != null && empresaDuplicada.getId_Empresa() != empresa.getId_Empresa()) {
                throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                        .getString("mensaje_empresa_duplicado_nombre"));
            }
        }
        //ID Fiscal
        empresaDuplicada = this.getEmpresaPorIdFiscal(empresa.getIdFiscal());
        if (operacion == TipoDeOperacion.ALTA && empresaDuplicada != null && empresa.getIdFiscal() != null) {
            throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                    .getString("mensaje_empresa_duplicado_cuip"));
        }
        if (operacion == TipoDeOperacion.ACTUALIZACION) {
            if (empresaDuplicada != null && empresaDuplicada.getId_Empresa() != empresa.getId_Empresa() && empresa.getIdFiscal() != null) {
                throw new BusinessServiceException(ResourceBundle.getBundle("Mensajes")
                        .getString("mensaje_empresa_duplicado_cuip"));
            }
        }
    }

    private void crearConfiguracionDelSistema(Empresa empresa) {
        ConfiguracionDelSistema cds = new ConfiguracionDelSistema();
        cds.setUsarFacturaVentaPreImpresa(false);
        cds.setCantidadMaximaDeRenglonesEnFactura(28);
        cds.setFacturaElectronicaHabilitada(false);
        cds.setEmpresa(empresa);
        configuracionDelSistemaService.guardar(cds);
    }

    @Override
    @Transactional
    public Empresa guardar(Empresa empresa) {
        validarOperacion(TipoDeOperacion.ALTA, empresa);
        empresa = empresaRepository.save(empresa);
        crearConfiguracionDelSistema(empresa);
        LOGGER.warn("La Empresa {} se guardó correctamente.", empresa);
        return empresa;
    }

    @Override
    @Transactional
    public void actualizar(Empresa empresa) {
        validarOperacion(TipoDeOperacion.ACTUALIZACION, empresa);
        empresaRepository.save(empresa);
    }

    @Override
    @Transactional
    public void eliminar(Long idEmpresa) {
        Empresa empresa = this.getEmpresaPorId(idEmpresa);
        empresa.setEliminada(true);
        configuracionDelSistemaService.eliminar(configuracionDelSistemaService.getConfiguracionDelSistemaPorEmpresa(empresa));
        empresaRepository.save(empresa);
    }

  @Override
  public String guardarLogo(long idEmpresa, byte[] imagen) {
    return photoVideoUploader.subirImagen(Empresa.class.getSimpleName() + idEmpresa, imagen);
  }
}
