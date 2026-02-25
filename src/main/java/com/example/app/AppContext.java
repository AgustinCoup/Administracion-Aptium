package com.example.app;

import com.example.features.autoclaves.dao.AutoclaveDAO;
import com.example.features.catalogo.dao.CatalogoDAO;
import com.example.features.clientes.dao.ClienteDAO;
import com.example.features.equipos.dao.EquipoDAO;
import com.example.features.instituciones.dao.InstitucionDAO;
import com.example.features.lotes.dao.LoteDAO;
import com.example.features.equipos.dao.MaterialDAO;
import com.example.features.profesionales.dao.ProfesionalDAO;
import com.example.features.autoclaves.service.AutoclaveService;
import com.example.features.lotes.service.CapacidadCalculatorImpl;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.clientes.service.ClienteService;
import com.example.features.equipos.service.EquipoService;
import com.example.features.equipos.service.EstadoValidatorImpl;
import com.example.features.lotes.service.ICapacidadCalculator;
import com.example.features.equipos.service.IEstadoValidator;
import com.example.features.equipos.service.IMaterialFilter;
import com.example.features.instituciones.service.InstitucionService;
import com.example.features.lotes.service.LoteService;
import com.example.features.equipos.service.MaterialFilterImpl;
import com.example.features.equipos.service.MaterialService;
import com.example.features.profesionales.service.ProfesionalService;

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
    private final IMaterialFilter materialFilter;
    private final ICapacidadCalculator capacidadCalculator;

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
        IMaterialFilter materialFilter,
        ICapacidadCalculator capacidadCalculator
    ) {
        if (equipoService == null || catalogoService == null || clienteService == null
            || profesionalService == null || institucionService == null || materialService == null
            || autoclaveService == null || loteService == null || estadoValidator == null
            || materialFilter == null || capacidadCalculator == null) {
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
        this.materialFilter = materialFilter;
        this.capacidadCalculator = capacidadCalculator;
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

        EquipoService equipoService = new EquipoService(equipoDAO);
        CatalogoService catalogoService = new CatalogoService(catalogoDAO);
        ClienteService clienteService = new ClienteService(clienteDAO);
        ProfesionalService profesionalService = new ProfesionalService(profesionalDAO);
        InstitucionService institucionService = new InstitucionService(institucionDAO);
        MaterialService materialService = new MaterialService(materialDAO);
        AutoclaveService autoclaveService = new AutoclaveService(autoclaveDAO);
        LoteService loteService = new LoteService(loteDAO);

        IEstadoValidator estadoValidator = new EstadoValidatorImpl();
        ICapacidadCalculator capacidadCalculator = new CapacidadCalculatorImpl();
        IMaterialFilter materialFilter = new MaterialFilterImpl(estadoValidator);

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
            materialFilter,
            capacidadCalculator
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

    public IMaterialFilter getMaterialFilter() {
        return materialFilter;
    }

    public ICapacidadCalculator getCapacidadCalculator() {
        return capacidadCalculator;
    }
}


