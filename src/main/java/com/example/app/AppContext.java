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
import com.example.features.lavadero.dao.BolsaLavaderoDAO;
import com.example.features.lavadero.dao.CatalogoElementosLavaderoDAO;
import com.example.features.lavadero.dao.ClasificacionLavaderoDAO;
import com.example.features.lavadero.dao.IngresoLavaderoDAO;
import com.example.features.lavadero.dao.CicloLavaderoDAO;
import com.example.features.lavadero.dao.LavarropasDAO;
import com.example.features.lavadero.dao.CatalogoJabonesDAO;
import com.example.features.lavadero.dao.SalidaLavaderoDAO;
import com.example.features.lavadero.dao.derivadores.AsignadorClienteAptium;
import com.example.features.lavadero.dao.derivadores.AsignadorClienteCDE;
import com.example.features.lavadero.dao.derivadores.ConstructorIngresoCDE;
import com.example.features.lavadero.dao.derivadores.DerivadorFueraDeFlujo;
import com.example.features.lavadero.dao.derivadores.DerivadorIngresoCDE;
import com.example.features.lavadero.dao.derivadores.DerivadorSalidas;
import com.example.features.lavadero.model.AccionSalida;
import com.example.features.lavadero.service.CatalogoJabonesService;
import com.example.features.lavadero.service.CicloLavaderoService;
import com.example.features.lavadero.service.ClasificacionLavaderoService;
import com.example.features.lavadero.service.LavarropasService;
import com.example.features.lavadero.service.LavaderoService;
import com.example.features.lavadero.service.SalidaLavaderoService;
import com.example.features.equipos.ortopedias.service.EquipoReporteService;
import com.example.features.equipos.otros.service.EquipoOtrosReporteService;
import com.example.features.lotes.service.LoteReporteService;

import java.util.List;

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
    private final LavaderoService lavaderoService;
    private final ClasificacionLavaderoService clasificacionLavaderoService;
    private final LavarropasService lavarropasService;
    private final CicloLavaderoService   cicloLavaderoService;
    private final CatalogoJabonesService catalogoJabonesService;
    private final SalidaLavaderoService  salidaLavaderoService;
    private final LoteReporteService loteReporteService;
    private final EquipoReporteService equipoReporteService;
    private final EquipoOtrosReporteService equipoOtrosReporteService;
    private final ActualizacionService actualizacionService;
    private final VersionInfo versionInfo;

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
        LavaderoService lavaderoService,
        ClasificacionLavaderoService clasificacionLavaderoService,
        LavarropasService lavarropasService,
        CicloLavaderoService cicloLavaderoService,
        CatalogoJabonesService catalogoJabonesService,
        SalidaLavaderoService salidaLavaderoService,
        LoteReporteService loteReporteService,
        EquipoReporteService equipoReporteService,
        EquipoOtrosReporteService equipoOtrosReporteService,
        ActualizacionService actualizacionService,
        VersionInfo versionInfo
    ) {
        if (equipoService == null || catalogoService == null || clienteService == null
            || profesionalService == null || institucionService == null || materialService == null
            || autoclaveService == null || loteService == null || estadoValidator == null
            || catalogoOtrosService == null
            || equipoOtrosService == null || equipoCorreccionService == null
            || equipoOtrosCorreccionService == null || lavaderoService == null
            || clasificacionLavaderoService == null
            || lavarropasService == null || cicloLavaderoService == null
            || catalogoJabonesService == null || salidaLavaderoService == null
            || loteReporteService == null
            || equipoReporteService == null || equipoOtrosReporteService == null
            || actualizacionService == null || versionInfo == null) {
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
        this.lavaderoService = lavaderoService;
        this.clasificacionLavaderoService = clasificacionLavaderoService;
        this.lavarropasService       = lavarropasService;
        this.cicloLavaderoService    = cicloLavaderoService;
        this.catalogoJabonesService  = catalogoJabonesService;
        this.salidaLavaderoService   = salidaLavaderoService;
        this.loteReporteService = loteReporteService;
        this.equipoReporteService = equipoReporteService;
        this.equipoOtrosReporteService = equipoOtrosReporteService;
        this.actualizacionService = actualizacionService;
        this.versionInfo = versionInfo;
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

        VersionInfo versionInfo = new VersionInfo();
        ActualizacionService actualizacionService = new ActualizacionService(
            new GithubReleaseClient(), versionInfo, new DescargaService(), new ActualizacionInstaller());

        BolsaLavaderoDAO bolsaLavaderoDAO = new BolsaLavaderoDAO();
        IngresoLavaderoDAO ingresoLavaderoDAO = new IngresoLavaderoDAO(bolsaLavaderoDAO);
        LavaderoService lavaderoService = new LavaderoService(ingresoLavaderoDAO);

        CatalogoElementosLavaderoDAO catalogoElementosLavaderoDAO = new CatalogoElementosLavaderoDAO();
        ClasificacionLavaderoDAO clasificacionLavaderoDAO = new ClasificacionLavaderoDAO();
        ClasificacionLavaderoService clasificacionLavaderoService = new ClasificacionLavaderoService(
            clasificacionLavaderoDAO, catalogoElementosLavaderoDAO);

        LavarropasDAO lavarropasDAO = new LavarropasDAO();
        LavarropasService lavarropasService = new LavarropasService(lavarropasDAO);
        CicloLavaderoDAO cicloLavaderoDAO = new CicloLavaderoDAO();
        CicloLavaderoService cicloLavaderoService = new CicloLavaderoService(cicloLavaderoDAO);
        CatalogoJabonesDAO catalogoJabonesDAO = new CatalogoJabonesDAO();
        CatalogoJabonesService catalogoJabonesService = new CatalogoJabonesService(catalogoJabonesDAO);

        // Las tres acciones de salida del lavadero. Esta lista es el unico lugar donde se
        // decide que acciones existen: agregar un destino nuevo es una entrada mas aca.
        SalidaLavaderoDAO salidaLavaderoDAO = new SalidaLavaderoDAO();
        ConstructorIngresoCDE constructorIngresoCDE = new ConstructorIngresoCDE();
        List<DerivadorSalidas> derivadores = List.of(
            new DerivadorFueraDeFlujo(),
            new DerivadorIngresoCDE(AccionSalida.CDE_CLIENTE, constructorIngresoCDE,
                                    AsignadorClienteCDE.CLIENTE_ORIGINAL, equipoOtrosDAO),
            new DerivadorIngresoCDE(AccionSalida.CDE_APTIUM, constructorIngresoCDE,
                                    new AsignadorClienteAptium(clienteDAO), equipoOtrosDAO));
        SalidaLavaderoService salidaLavaderoService =
            new SalidaLavaderoService(salidaLavaderoDAO, derivadores);

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
            lavaderoService,
            clasificacionLavaderoService,
            lavarropasService,
            cicloLavaderoService,
            catalogoJabonesService,
            salidaLavaderoService,
            loteReporteService,
            equipoReporteService,
            equipoOtrosReporteService,
            actualizacionService,
            versionInfo
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

    public LavaderoService getLavaderoService() {
        return lavaderoService;
    }

    public ClasificacionLavaderoService getClasificacionLavaderoService() {
        return clasificacionLavaderoService;
    }

    public LavarropasService getLavarropasService() {
        return lavarropasService;
    }

    public CicloLavaderoService getCicloLavaderoService() {
        return cicloLavaderoService;
    }

    public CatalogoJabonesService getCatalogoJabonesService() {
        return catalogoJabonesService;
    }

    public SalidaLavaderoService getSalidaLavaderoService() {
        return salidaLavaderoService;
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

    public VersionInfo getVersionInfo() {
        return versionInfo;
    }
}


