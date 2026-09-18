package com.example.features.equipos.ortopedias.controller;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Objects;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.paginacion.Pagina;
import com.example.features.equipos.model.FiltroEquipos;
import com.example.features.equipos.ortopedias.controller.helpers.ConsultaCde;
import com.example.features.equipos.ortopedias.view.PantallaVerCDEv2;

/**
 * Controlador para {@link PantallaVerCDEv2}: equipos de ortopedia y "otros" en una única grilla
 * paginada, con los filtros y el orden resueltos en SQL.
 *
 * <h2>Ya no hay snapshot completo</h2>
 * Este controller <b>no</b> extiende {@code AbstractFilterController} y no tiene {@code cache}: la
 * lista completa no se lee nunca más. Los tres filtros —cliente, institución y estados— viajan a la
 * base y vuelve una página de 50, ya ordenada. Dejar la herencia con un cache vacío sería peor que
 * sacarla: invita a filtrar en memoria una página, que da 50 de 5000 en vez de las 50 primeras de
 * las que matchean.
 *
 * <h2>Sigue comiendo del histórico completo, y el default sigue ocultando los entregados</h2>
 * La pantalla deja filtrar por ENTREGADO: no come de la cola activa. Lo que cambió es <b>dónde</b>
 * se aplica el default que los oculta —{@link PantallaVerCDEv2#aplicarFiltroInicial()}—: antes era
 * un filtro de la vista sobre datos completos, ahora es parte del {@code WHERE}. El combo se puebla
 * de {@code EstadoEquipo.values()} y no de los datos, así que destildar ENTREGADO dispara una
 * consulta nueva que los trae; ver el javadoc de ese método, que refuta por escrito el argumento
 * con el que se justificaba la versión anterior.
 *
 * <h2>El filtro por institución excluye a los "otros" — y eso se preserva</h2>
 * {@code equipo_otros} no tiene institución. Con el campo escrito los "otros" desaparecen de la
 * grilla; con el campo en blanco aparecen todos. Es el comportamiento que el operador conoce y que
 * {@code CdeConsultaDAO} reproduce en SQL; arreglarlo sería un cambio visible decidido aparte.
 *
 * <h2>El filtro y la página se publican, no se leen desde el hilo de fondo</h2>
 * {@code RefrescadorPantallas} lee con un {@code Supplier} sin parámetros que corre fuera del EDT.
 * Todo cambio publica una {@link ConsultaCde} nueva —inmutable, en el EDT— en {@link #consulta}, y
 * el lector lee esa referencia. Nada de este controller se toca desde el hilo de fondo salvo ese
 * campo {@code volatile}.
 */
public class EstadoProcesosController {

    private final PantallaVerCDEv2 panel;
    private final Runnable         solicitarRefresco;

    /**
     * Lo que el lector de fondo tiene que leer. <b>Se escribe sólo en el EDT</b> y se lee desde el
     * hilo de fondo; por eso es {@code volatile} y por eso lo que guarda es inmutable.
     */
    private volatile ConsultaCde consulta = ConsultaCde.primeraPagina(FiltroEquipos.sinFiltros());

    /**
     * Alcance: pintar la grilla unificada desde el refresco.
     *
     * <p>No recibe el service: la lectura la arma {@code UiCoordinator} sobre
     * {@link #consultaActual()}, que es lo que hace que corra en el hilo de fondo sin tocar estado
     * del EDT.
     */
    public EstadoProcesosController(PantallaVerCDEv2 panel, Runnable solicitarRefresco) {
        this.panel             = Objects.requireNonNull(panel, "panel");
        this.solicitarRefresco = Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

        panel.setOnFiltrosChanged(this::alCambiarFiltros);
        panel.setAlCambiarPagina(this::alCambiarPagina);

        // El botón "Actualizar" (y F5) releen la misma página con el mismo filtro, con el total al
        // día; no toca aplicarFiltroInicial() para no pisar los filtros que el operador puso.
        panel.setAccionRefrescar(this::refrescar);

        panel.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                // Sin notificar: la relectura la pide alCambiarFiltros(), y pintar() es el único
                // que repinta, para no mostrar un flash con la página de la visita anterior.
                panel.aplicarFiltroInicial();
                alCambiarFiltros();
            }
        });
    }

    /**
     * Qué tiene que leer el hilo de fondo. Es el único miembro de esta clase que se puede tocar
     * fuera del EDT.
     */
    public ConsultaCde consultaActual() {
        return consulta;
    }

    /** Vuelca la página a la grilla y a la barra de paginación. Corre en el hilo de UI, sin I/O. */
    public void pintar(Pagina<EquipoRegistrableInterface> pagina) {
        // El total recién leído se arrastra: el próximo cambio de página no vuelve a contar.
        consulta = consulta.conTotal(pagina.totalFilas());
        panel.actualizarTabla(pagina.contenido());
        panel.mostrarPaginacion(pagina);
        panel.marcarActualizado();
    }

    // ── Disparadores ──────────────────────────────────────────────────────────

    /** Filtro nuevo ⇒ página 1 y total nuevo. */
    private void alCambiarFiltros() {
        publicarYPedir(ConsultaCde.primeraPagina(filtroDeLaPantalla()));
    }

    /** Otra página del mismo filtro: conserva filtro y total. */
    private void alCambiarPagina(int numeroPagina) {
        publicarYPedir(consulta.enPagina(numeroPagina));
    }

    /** Refresco pedido por el operador: la misma página y el mismo filtro, contando de nuevo. */
    private void refrescar() {
        publicarYPedir(consulta.recontando());
    }

    /**
     * Publica qué hay que leer y pide la lectura, en ese orden y siempre juntos: el
     * {@code solicitar()} cancela lo que haya en vuelo, y así lo que llegue a {@code pintar}
     * corresponde a lo último publicado.
     */
    private void publicarYPedir(ConsultaCde nueva) {
        consulta = nueva;
        solicitarRefresco.run();
    }

    /**
     * Los tres filtros de esta pantalla dentro del record que comparten las dos del CDE. Los cuatro
     * campos que esta pantalla no ofrece —profesional, paciente, tipo de ingreso y fechas— van
     * vacíos, que es "sin filtro".
     */
    private FiltroEquipos filtroDeLaPantalla() {
        return new FiltroEquipos(
            panel.getFiltroEstados(),
            panel.getFiltroCliente(),
            null,
            null,
            panel.getFiltroInstitucion(),
            List.of(),
            null,
            null);
    }
}
