package com.example.app.ui;

import com.example.app.AppModel;
import com.example.features.equipos.controller.CDEViewController;
import com.example.features.equipos.controller.EquiposParaEntregarController;
import com.example.features.equipos.controller.OrthopediaInputController;
import com.example.features.equipos.controller.RegistrarEstadoController;
import com.example.features.lotes.controller.LotesController;
import com.example.ui.events.OnEquipoGuardadoListener;
import com.example.ui.events.OnEstadosActualizadosListener;
import com.example.ui.shell.PantallaPrincipal;

public class UiCoordinator {

    private final AppModel model;
    private final PantallaPrincipal vista;

    public UiCoordinator(AppModel model, PantallaPrincipal vista) {
        if (model == null || vista == null) {
            throw new IllegalArgumentException("Model y vista no pueden ser nulos");
        }
        this.model = model;
        this.vista = vista;
    }

    public void inicializar() {
        CDEViewController cdeViewController = new CDEViewController(
            vista.getPantallaVerCDEv2(),
            model
        );

        RegistrarEstadoController registrarEstadoController = new RegistrarEstadoController(
            vista.getPantallaRegistrarEstado(),
            model,
            null
        );

        EquiposParaEntregarController equiposParaEntregarController = new EquiposParaEntregarController(
            vista.getPantallaEquiposParaEntregar(),
            model,
            null
        );

        LotesController lotesController = new LotesController(
            vista.getPantallaLotes(),
            model,
            null
        );

        Runnable refrescarPantallas = crearRefrescador(
            cdeViewController,
            registrarEstadoController,
            equiposParaEntregarController,
            lotesController
        );

        OnEstadosActualizadosListener refrescarPantallasEstados = refrescarPantallas::run;
        OnEquipoGuardadoListener refrescarPantallasEquipos = refrescarPantallas::run;

        registrarEstadoController.setOnEstadosActualizados(refrescarPantallasEstados);
        equiposParaEntregarController.setOnEstadosActualizados(refrescarPantallasEstados);
        lotesController.setOnEstadosActualizados(refrescarPantallasEstados);

        new OrthopediaInputController(
            vista.getPanelIngresoOrtopedia(),
            model,
            vista.getNavegador(),
            vista.getContenedor(),
            refrescarPantallasEquipos
        );
    }

    private Runnable crearRefrescador(
        CDEViewController cdeViewController,
        RegistrarEstadoController registrarEstadoController,
        EquiposParaEntregarController equiposParaEntregarController,
        LotesController lotesController
    ) {
        return () -> {
            cdeViewController.cargarDatos();
            registrarEstadoController.cargarEquipos();
            equiposParaEntregarController.cargarDatos();
            lotesController.cargarDatos();
        };
    }
}


