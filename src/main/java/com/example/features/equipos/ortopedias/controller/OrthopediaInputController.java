package com.example.features.equipos.ortopedias.controller;

import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import com.example.features.catalogo.service.CatalogoService;
import com.example.features.clientes.service.ClienteService;
import com.example.features.equipos.common.controller.EquipoInputControllerBase;
import com.example.features.equipos.ortopedias.controller.helpers.CatalogoLookup;
import com.example.features.equipos.ortopedias.controller.helpers.ConstructorEquipo;
import com.example.features.equipos.ortopedias.controller.helpers.GestorNuevasEntidades;
import com.example.features.equipos.ortopedias.controller.helpers.GestorValidacionFormulario;
import com.example.features.equipos.ortopedias.service.EquipoService;
import com.example.features.instituciones.model.Institucion;
import com.example.features.instituciones.service.InstitucionService;
import com.example.features.profesionales.model.Profesional;
import com.example.features.profesionales.service.ProfesionalService;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.view.PantallaIngresoOrtopedia;
import com.example.ui.common.AutocompleteListener;
import com.example.ui.events.OnEquipoGuardadoListener;

import java.awt.CardLayout;
import javax.swing.JPanel;

public class OrthopediaInputController extends EquipoInputControllerBase<PantallaIngresoOrtopedia> {

    private final GestorValidacionFormulario gestorValidacion;
    private final ConstructorEquipo          constructorEquipo;

    private GestorNuevasEntidades<Profesional> gestorNuevosProfesionales;
    private GestorNuevasEntidades<Institucion> gestorNuevasInstituciones;

    private AutocompleteListener<Profesional> autocompleteProfesionalListener;
    private AutocompleteListener<Institucion> autocompleteInstitucionListener;

    private final CatalogoService     catalogoService;
    private final ProfesionalService  profesionalService;
    private final InstitucionService  institucionService;
    private final EquipoService       equipoService;

    /**
     * Alcance: alta de un ingreso de ortopedia, con autocompletado contra el
     * catálogo de códigos, profesionales e instituciones.
     */
    public OrthopediaInputController(PantallaIngresoOrtopedia panel,
                                     ClienteService clienteService,
                                     CatalogoService catalogoService,
                                     ProfesionalService profesionalService,
                                     InstitucionService institucionService,
                                     EquipoService equipoService,
                                     CardLayout navegador, JPanel contenedor,
                                     OnEquipoGuardadoListener onEquipoGuardadoListener) {
        super(panel, clienteService, navegador, contenedor, onEquipoGuardadoListener);
        this.catalogoService    = catalogoService;
        this.profesionalService = profesionalService;
        this.institucionService = institucionService;
        this.equipoService      = equipoService;

        CatalogoLookup catalogoLookup = codigo -> catalogoService.obtenerDescripcion(codigo) != null;
        this.gestorValidacion  = new GestorValidacionFormulario(panel, catalogoLookup);
        this.constructorEquipo = new ConstructorEquipo(panel);

        inicializarEventosComunes();
        inicializarEventosEspecificos();
    }

    private void inicializarEventosEspecificos() {
        panel.getPanelMateriales().setOnNumeroChangedListener((codigo, campoDescripcion) -> {
            String descripcion = catalogoService.obtenerDescripcion(codigo);
            campoDescripcion.setText(
                descripcion != null ? descripcion : Constantes.Mensajes.AUTOCOMPLETE_DESCONOCIDO);
        });

        autocompleteProfesionalListener = new AutocompleteListener<>(
            panel.getTxtProfesional(),
            profesionalService::buscarProfesionales,
            profesional -> panel.setSelectedProfesionalId(profesional.getId()),
            nombre -> gestorNuevosProfesionales.manejarEntidadNoExistente(nombre)
        );

        autocompleteInstitucionListener = new AutocompleteListener<>(
            panel.getTxtInstitucion(),
            institucionService::buscarInstituciones,
            institucion -> panel.setSelectedInstitucionId(institucion.getId()),
            nombre -> gestorNuevasInstituciones.manejarEntidadNoExistente(nombre)
        );

        gestorNuevosProfesionales = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_PROFESIONAL,
            nombre -> panel.getTxtProfesional().setText(nombre),
            id     -> panel.setSelectedProfesionalId(id),
            autocompleteProfesionalListener,
            profesionalService::guardarProfesional,
            Profesional::new
        );

        gestorNuevasInstituciones = new GestorNuevasEntidades<>(
            obtenerVentanaParente(),
            Constantes.Textos.ENTIDAD_INSTITUCION,
            nombre -> panel.getTxtInstitucion().setText(nombre),
            id     -> panel.setSelectedInstitucionId(id),
            autocompleteInstitucionListener,
            institucionService::guardarInstitucion,
            Institucion::new
        );
    }

    @Override
    protected void guardar() {
        if (!gestorValidacion.validar()) return;

        if (panel.getPanelMateriales().tieneDuplicados()) {
            panel.mostrarAdvertencia(
                "Hay materiales con el mismo código de catálogo.\n" +
                "Unifique las filas marcadas en rojo antes de guardar.");
            return;
        }

        Equipo equipo = constructorEquipo.construir();

        boolean exito;
        try {
            exito = equipoService.guardarEquipo(equipo);
        } catch (ValidationException e) {
            String mensaje = e.getValidationErrors().isEmpty()
                ? Constantes.Mensajes.ERROR_GUARDAR_EQUIPO
                : String.join("\n", e.getValidationErrors());
            panel.mostrarAdvertencia(mensaje);
            log.warn("Validación de negocio al guardar equipo: {}", mensaje);
            return;
        }

        manejarResultadoGuardado(exito, Constantes.Mensajes.DATOS_GUARDADOS,
            Constantes.Pantallas.INGRESO_ORTOPEDIA, "ortopedia");
    }
}
