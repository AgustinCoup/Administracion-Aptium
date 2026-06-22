package com.example.ui.shell;

import javax.swing.*;
import java.awt.*;
import com.example.common.constants.Constantes;
import com.example.features.equipos.ortopedias.view.PantallaAuditoria;
import com.example.features.equipos.ortopedias.view.PantallaCorrecciones;
import com.example.features.equipos.ortopedias.view.PantallaEquiposParaEntregar;
import com.example.features.equipos.ortopedias.view.PantallaIngresoOrtopedia;
import com.example.features.equipos.ortopedias.view.PantallaRegistrarEstado;
import com.example.features.equipos.otros.view.PantallaIngresoOtros;
import com.example.features.equipos.view.PantallaVerEquipos;
import com.example.features.equipos.ortopedias.view.PantallaVerCDEv1;
import com.example.features.equipos.ortopedias.view.PantallaVerCDEv2;
import com.example.features.ajustes.view.PantallaAjustes;
import com.example.features.lotes.view.PantallaLotes;
import com.example.features.lotes.view.PantallaVerLotes;

/**
 * Ventana principal de la aplicación.
 * Administra el CardLayout que contiene todas las pantallas.
 *
 * Todas las pantallas se crean aquí y se exponen mediante getters para que
 * UiCoordinator pueda inyectar servicios y cablear listeners.
 */
public class PantallaPrincipal extends JFrame {

    private final CardLayout navegador  = new CardLayout();
    private final JPanel     contenedor = new JPanel(navegador);

    // ── Pantallas ─────────────────────────────────────────────────────────────
    private final PantallaIngresoOrtopedia      ingresoOrtopedia;
    private final PantallaVerCDEv2              verCDEv2;
    private final PantallaRegistrarEstado       registrarEstado;
    private final PantallaEquiposParaEntregar   equiposParaEntregar;
    private final PantallaCorrecciones          correcciones;
    private final PantallaLotes                 pantallaLotes;
    private final PantallaVerLotes              pantallaVerLotes;
    private final PantallaAuditoria             pantallaAuditoria;
    private final PantallaIngresoOtros          ingresoOtros;
    private final PantallaVerEquipos            verEquipos;
    private final PantallaAjustes               pantallaAjustes;

    public PantallaPrincipal() {
        setTitle(Constantes.Titulos.APP);
        setSize(1280, 720);
        setMinimumSize(new Dimension(1280, 720));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // ── Instanciar pantallas ─────────────────────────────────────────────
        PantallaMenu           menu           = new PantallaMenu(navegador, contenedor);
        PantallaEsterilizacion esterilizacion = new PantallaEsterilizacion(navegador, contenedor);
        PantallaEsOrtopedia    esOrtopedia    = new PantallaEsOrtopedia(navegador, contenedor);
        PantallaVerCDEv1       verCDE         = new PantallaVerCDEv1(navegador, contenedor);

        verCDEv2            = new PantallaVerCDEv2(navegador, contenedor);
        ingresoOrtopedia    = new PantallaIngresoOrtopedia(navegador, contenedor);
        registrarEstado     = new PantallaRegistrarEstado(navegador, contenedor);
        equiposParaEntregar = new PantallaEquiposParaEntregar(navegador, contenedor);
        correcciones        = new PantallaCorrecciones(navegador, contenedor);
        pantallaLotes       = new PantallaLotes(navegador, contenedor);
        pantallaVerLotes    = new PantallaVerLotes(navegador, contenedor);
        pantallaAuditoria   = new PantallaAuditoria(navegador, contenedor);
        ingresoOtros        = new PantallaIngresoOtros(navegador, contenedor);
        verEquipos          = new PantallaVerEquipos(navegador, contenedor);
        pantallaAjustes     = new PantallaAjustes(navegador, contenedor);
        // ── Registrar en el CardLayout ────────────────────────────────────────
        contenedor.add(menu,                Constantes.Pantallas.MENU_PRINCIPAL);
        contenedor.add(esterilizacion,      Constantes.Pantallas.ESTERILIZACION);
        contenedor.add(esOrtopedia,         Constantes.Pantallas.ES_ORTOPEDIA);
        contenedor.add(verCDE,              Constantes.Pantallas.VER_CDE);
        contenedor.add(verCDEv2,            Constantes.Pantallas.VER_CDE_V2);
        contenedor.add(ingresoOrtopedia,    Constantes.Pantallas.INGRESO_ORTOPEDIA);
        contenedor.add(registrarEstado,     Constantes.Pantallas.REGISTRAR_ESTADO);
        contenedor.add(equiposParaEntregar, Constantes.Pantallas.EQUIPOS_PARA_ENTREGAR);
        contenedor.add(correcciones,        Constantes.Pantallas.CORRECCIONES);
        contenedor.add(pantallaLotes,       Constantes.Pantallas.LOTES);
        contenedor.add(pantallaVerLotes,    Constantes.Pantallas.VER_LOTES);
        contenedor.add(pantallaAuditoria,   Constantes.Pantallas.AUDITORIA);
        contenedor.add(ingresoOtros,        Constantes.Pantallas.INGRESO_OTROS);
        contenedor.add(verEquipos,          Constantes.Pantallas.VER_EQUIPOS);
        contenedor.add(pantallaAjustes,     Constantes.Pantallas.AJUSTES);
        add(contenedor);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public PantallaIngresoOrtopedia    getPanelIngresoOrtopedia()      { return ingresoOrtopedia; }
    public PantallaIngresoOtros        getPanelIngresoOtros()          { return ingresoOtros; }
    public PantallaVerCDEv2            getPantallaVerCDEv2()           { return verCDEv2; }
    public PantallaRegistrarEstado     getPantallaRegistrarEstado()    { return registrarEstado; }
    public PantallaEquiposParaEntregar getPantallaEquiposParaEntregar(){ return equiposParaEntregar; }
    public PantallaCorrecciones        getPantallaCorrecciones()       { return correcciones; }
    public PantallaLotes               getPantallaLotes()              { return pantallaLotes; }
    public PantallaVerLotes            getPantallaVerLotes()           { return pantallaVerLotes; }
    public PantallaAuditoria           getPantallaAuditoria()          { return pantallaAuditoria; }
    public PantallaVerEquipos          getPantallaVerEquipos()         { return verEquipos; }
    public PantallaAjustes             getPantallaAjustes()            { return pantallaAjustes; }
    public CardLayout                  getNavegador()                  { return navegador; }
    public JPanel                      getContenedor()                 { return contenedor; }
}