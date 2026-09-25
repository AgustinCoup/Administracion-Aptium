package com.example.ui.common.seleccion;

import com.example.ui.common.seleccion.ReglasSeleccionAcumulativa.Seleccion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static com.example.ui.common.seleccion.ReglasSeleccionAcumulativa.SIN_ANCLA;
import static com.example.ui.common.seleccion.ReglasSeleccionAcumulativa.conservarAlPerderFoco;
import static com.example.ui.common.seleccion.ReglasSeleccionAcumulativa.sumarAnterior;
import static com.example.ui.common.seleccion.ReglasSeleccionAcumulativa.sumarSiguiente;
import static org.junit.jupiter.api.Assertions.*;

class ReglasSeleccionAcumulativaTest {

    private static final int FILAS = 5;

    @Test
    void sumarSiguiente_agregaLaFilaDeAbajoYMueveElAncla() {
        Seleccion nueva = sumarSiguiente(sel(2, 2), FILAS);

        assertEquals(sel(2, 2, 3).filas(), nueva.filas());
        assertEquals(3, nueva.ancla());
    }

    @Test
    void sumarAnterior_agregaLaFilaDeArribaYMueveElAncla() {
        Seleccion nueva = sumarAnterior(sel(2, 2), FILAS);

        assertEquals(List.of(1, 2), List.copyOf(nueva.filas()));
        assertEquals(1, nueva.ancla());
    }

    @Test
    void sumarSiguiente_filaYaSeleccionada_noQuitaNadaYMueveElAncla() {
        Seleccion nueva = sumarSiguiente(sel(1, 1, 2), FILAS);

        assertEquals(List.of(1, 2), List.copyOf(nueva.filas()));
        assertEquals(2, nueva.ancla());
    }

    @Test
    void sumarSiguiente_enLaUltimaFila_noCambia() {
        Seleccion actual = sel(4, 3, 4);

        assertEquals(actual, sumarSiguiente(actual, FILAS));
    }

    @Test
    void sumarAnterior_enLaPrimeraFila_noCambia() {
        Seleccion actual = sel(0, 0, 1);

        assertEquals(actual, sumarAnterior(actual, FILAS));
    }

    @Test
    void sumarSiguiente_sinSeleccion_noCambia() {
        Seleccion actual = sel(2);

        assertEquals(actual, sumarSiguiente(actual, FILAS));
        assertEquals(actual, sumarAnterior(actual, FILAS));
    }

    @Test
    void sumarSiguiente_sinAncla_noCambia() {
        Seleccion actual = sel(SIN_ANCLA, 1);

        assertEquals(actual, sumarSiguiente(actual, FILAS));
    }

    @Test
    void sumarSiguiente_anclaDeseleccionadaPorCtrlClick_sumaLaDeAbajoDelAncla() {
        // Selección {0, 1, 2}; Ctrl+click en 1 la deselecciona y la deja de lead.
        Seleccion nueva = sumarSiguiente(sel(1, 0, 2), FILAS);

        assertEquals(List.of(0, 2), List.copyOf(nueva.filas()));
        assertEquals(2, nueva.ancla());
    }

    @Test
    void sumarSiguiente_nuncaQuitaFilasNoContiguas() {
        Seleccion nueva = sumarSiguiente(sel(1, 1, 4), FILAS);

        assertEquals(List.of(1, 2, 4), List.copyOf(nueva.filas()));
        assertEquals(2, nueva.ancla());
    }

    @Test
    void seleccion_esInmutable() {
        TreeSet<Integer> origen = new TreeSet<>(List.of(1));
        Seleccion s = new Seleccion(origen, 1);

        origen.add(3);

        assertEquals(List.of(1), List.copyOf(s.filas()));
        assertThrows(UnsupportedOperationException.class, () -> s.filas().add(2));
    }

    @Test
    void conservarAlPerderFoco_temporalYEnLaZona_conserva() {
        assertTrue(conservarAlPerderFoco(true, true));
    }

    @Test
    void conservarAlPerderFoco_temporalFueraDeLaZona_conserva() {
        assertTrue(conservarAlPerderFoco(true, false));
    }

    @Test
    void conservarAlPerderFoco_permanenteEnLaZona_conserva() {
        assertTrue(conservarAlPerderFoco(false, true));
    }

    @Test
    void conservarAlPerderFoco_permanenteFueraDeLaZona_vacia() {
        assertFalse(conservarAlPerderFoco(false, false));
    }

    private static Seleccion sel(int ancla, Integer... filas) {
        return new Seleccion(new TreeSet<>(List.of(filas)), ancla);
    }
}
