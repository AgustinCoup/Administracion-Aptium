package com.example.app;

import com.example.common.VersionInfo;
import com.example.features.actualizaciones.service.ActualizacionInstaller;
import com.example.features.actualizaciones.service.ActualizacionService;
import com.example.features.actualizaciones.service.DescargaService;
import com.example.features.actualizaciones.service.GithubReleaseClient;
import com.example.features.autoclaves.dao.AutoclaveDAO;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.clientes.dao.FusionClientesDAO;
import com.example.features.instituciones.dao.InstitucionDAO;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.profesionales.dao.ProfesionalDAO;
import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.clientes.service.ClienteService;
import com.example.features.equipos.ortopedias.dao.AuditoriaDAO;
import com.example.features.equipos.ortopedias.dao.EquipoDAO;
import com.example.features.equipos.ortopedias.dao.MaterialDAO;
import com.example.features.equipos.ortopedias.service.EquipoCorreccionService;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.equipos.ortopedias.service.EstadoValidatorImpl;
import com.example.features.equipos.ortopedias.service.IEstadoValidator;
import com.example.features.equipos.ortopedias.service.MaterialService;
import com.example.features.instituciones.service.InstitucionService;
import com.example.features.lotes.service.LoteService;
import com.example.features.profesionales.service.ProfesionalService;
import com.example.features.catalogo.dao.CatalogoOtrosDAO;
import com.example.features.equipos.otros.dao.EquipoOtrosDAO;
import com.example.features.catalogo.service.CatalogoOtrosService;
import com.example.features.equipos.otros.service.EquipoOtrosCorreccionService;
import com.example.features.equipos.otros.service.EquipoOtrosService;
import com.example.features.equipos.ortopedias.service.EquipoReporteService;
import com.example.features.equipos.otros.service.EquipoOtrosReporteService;
import com.example.features.lotes.service.LoteReporteService;

public class AppContext {

    private final EquipoService equipoService;
    private final CatalogoService catalogoService;
    private final ClienteService clienteService;
    private final ProfesionalService profesionalService;
    private final InstitucionService institucionService;
    private final MaterialService materialService;
    private final AutoclaveService autoclaveService;
    private final LoteService loteService;
    private final IEstadoValidator estadoValidator;
    private final CatalogoOtrosService catalogoOtrosService;
    private final EquipoOtrosService equipoOtrosService;
    private final EquipoCorreccionService equipoCorreccionService;
    private final EquipoOtrosCorreccionService equipoOtrosCorreccionService;
    private final LoteReporteService loteReporteService;
    private final EquipoReporteService equipoReporteService;
    private final EquipoOtrosReporteService equipoOtrosReporteService;
    private final ActualizacionService actualizacionService;

    public AppContext(
        EquipoService equipoService,
        CatalogoService catalogoService,
        ClienteService clienteService,
        ProfesionalService profesionalService,
        InstitucionService institucionService,
        MaterialService materialService,
        AutoclaveService autoclaveService,
        LoteService loteService,
        IEstadoValidator estadoValidator,
        CatalogoOtrosService catalogoOtrosService,
        EquipoOtrosService equipoOtrosService,
        EquipoCorreccionService equipoCorreccionService,
        EquipoOtrosCorreccionService equipoOtrosCorreccionService,
        LoteReporteService loteReporteService,
        EquipoReporteService equipoReporteService,
        EquipoOtrosReporteService equipoOtrosReporteService,
        ActualizacionService actualizacionService
    ) {
        if (equipoService == null || catalogoService == null || clienteService == null
            || profesionalService == null || institucionService == null || materialService == null
            || autoclaveService == null || loteService == null || estadoValidator == null
            || catalogoOtrosService == null
            || equipoOtrosService == null || equipoCorreccionService == null
            || equipoOtrosCorreccionService == null || loteReporteService == null
            || equipoReporteService == null || equipoOtrosReporteService == null
            || actualizacionService == null) {
            throw new IllegalArgumentException("AppContext requiere dependencias no nulas");
        }

        this.equipoService = equipoService;
        this.catalogoService = catalogoService;
        this.clienteService = clienteService;
        this.profesionalService = profesionalService;
        this.institucionService = institucionService;
        this.materialService = materialService;
        this.autoclaveService = autoclaveService;
        this.loteService = loteService;
        this.estadoValidator = estadoValidator;
        this.catalogoOtrosService = catalogoOtrosService;
        this.equipoOtrosService = equipoOtrosService;
        this.equipoCorreccionService = equipoCorreccionService;
        this.equipoOtrosCorreccionService = equipoOtrosCorreccionService;
        this.loteReporteService = loteReporteService;
        this.equipoReporteService = equipoReporteService;
        this.equipoOtrosReporteService = equipoOtrosReporteService;
        this.actualizacionService = actualizacionService;
    }

    public static AppContext createDefault() {
        EquipoDAO equipoDAO = new EquipoDAO();
        CatalogoDAO catalogoDAO = new CatalogoDAO();
        ClienteDAO clienteDAO = new ClienteDAO();
        ProfesionalDAO profesionalDAO = new ProfesionalDAO();
        InstitucionDAO institucionDAO = new InstitucionDAO();
        MaterialDAO materialDAO = new MaterialDAO();
        AutoclaveDAO autoclaveDAO = new AutoclaveDAO();
        LoteDAO loteDAO = new LoteDAO();
        CatalogoOtrosDAO catalogoOtrosDAO = new CatalogoOtrosDAO();
        EquipoOtrosDAO equipoOtrosDAO = new EquipoOtrosDAO(catalogoOtrosDAO);

        AuditoriaDAO auditoriaDAO = new AuditoriaDAO();

        EquipoService equipoService = new EquipoService(equipoDAO);
        EquipoCorreccionService equipoCorreccionService = new EquipoCorreccionService(
            equipoDAO, materialDAO, auditoriaDAO, catalogoDAO);
        EquipoOtrosCorreccionService equipoOtrosCorreccionService = new EquipoOtrosCorreccionService(
            equipoOtrosDAO, auditoriaDAO);
        EquipoOtrosService equipoOtrosService = new EquipoOtrosService(equipoOtrosDAO);
        CatalogoService catalogoService = new CatalogoService(catalogoDAO);
        CatalogoOtrosService catalogoOtrosService = new CatalogoOtrosService(catalogoOtrosDAO);
        FusionClientesDAO fusionClientesDAO = new FusionClientesDAO();
        ClienteService clienteService = new ClienteService(clienteDAO, fusionClientesDAO);
        ProfesionalService profesionalService = new ProfesionalService(profesionalDAO);
        InstitucionService institucionService = new InstitucionService(institucionDAO);
        MaterialService materialService = new MaterialService(materialDAO);
        AutoclaveService autoclaveService = new AutoclaveService(autoclaveDAO);
        LoteService loteService = new LoteService(loteDAO);

        IEstadoValidator estadoValidator = new EstadoValidatorImpl();

        LoteReporteService loteReporteService = new LoteReporteService(loteService);
        EquipoReporteService equipoReporteService = new EquipoReporteService(equipoService);
        EquipoOtrosReporteService equipoOtrosReporteService =
            new EquipoOtrosReporteService(equipoOtrosService);

        ActualizacionService actualizacionService = new ActualizacionService(
            new GithubReleaseClient(), new VersionInfo(), new DescargaService(), new ActualizacionInstaller());

        return new AppContext(
            equipoService,
            catalogoService,
            clienteService,
            profesionalService,
            institucionService,
            materialService,
            autoclaveService,
            loteService,
            estadoValidator,
            catalogoOtrosService,
            equipoOtrosService,
            equipoCorreccionService,
            equipoOtrosCorreccionService,
            loteReporteService,
            equipoReporteService,
            equipoOtrosReporteService,
            actualizacionService
        );
    }

    public EquipoService getEquipoService() {
        return equipoService;
    }

    public CatalogoService getCatalogoService() {
        return catalogoService;
    }

    public ClienteService getClienteService() {
        return clienteService;
    }

    public ProfesionalService getProfesionalService() {
        return profesionalService;
    }

    public InstitucionService getInstitucionService() {
        return institucionService;
    }

    public MaterialService getMaterialService() {
        return materialService;
    }

    public AutoclaveService getAutoclaveService() {
        return autoclaveService;
    }

    public LoteService getLoteService() {
        return loteService;
    }

    public IEstadoValidator getEstadoValidator() {
        return estadoValidator;
    }

    public CatalogoOtrosService getCatalogoOtrosService() {
        return catalogoOtrosService;
    }

    public EquipoOtrosService getEquipoOtrosService() {
        return equipoOtrosService;
    }

    public EquipoCorreccionService getEquipoCorreccionService() {
        return equipoCorreccionService;
    }

    public EquipoOtrosCorreccionService getEquipoOtrosCorreccionService() {
        return equipoOtrosCorreccionService;
    }

    public LoteReporteService getLoteReporteService() {
        return loteReporteService;
    }

    public EquipoReporteService getEquipoReporteService() {
        return equipoReporteService;
    }

    public EquipoOtrosReporteService getEquipoOtrosReporteService() {
        return equipoOtrosReporteService;
    }

    public ActualizacionService getActualizacionService() {
        return actualizacionService;
    }
}


