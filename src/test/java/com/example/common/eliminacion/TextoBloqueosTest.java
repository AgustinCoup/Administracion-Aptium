package com.example.common.eliminacion;

import com.example.common.constants.Constantes.Mensajes;
import com.example.common.exception.BusinessException;
import com.example.common.exception.ConflictoConcurrenciaException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextoBloqueosTest {

    @Test
    void describir_unaLineaPorBloqueoYDiceQueHacer() {
        String texto = TextoBloqueos.describir(List.of(
            new Bloqueo.LoteEnCurso("2026-15"),
            new Bloqueo.CicloEnCurso(4),
            new Bloqueo.DerivadoCompartido(31, List.of(7, 9)),
            new Bloqueo.DerivadoEnLoteEnCurso(31, "2026-16")));

        String[] lineas = texto.split("\n");
        assertEquals(4, lineas.length);
        assertTrue(lineas[0].contains("2026-15") && lineas[0].contains("Finalizá ese lote"), lineas[0]);
        assertTrue(lineas[1].contains("lavarropas 4") && lineas[1].contains("Finalizá ese ciclo"), lineas[1]);
        assertTrue(lineas[2].contains("#31") && lineas[2].contains("#7, #9")
            && lineas[2].contains("Ver Equipos → Otros"), lineas[2]);
        assertTrue(lineas[3].contains("#31") && lineas[3].contains("2026-16"), lineas[3]);
    }

    @Test
    void excepcion_llevaTodosLosBloqueosYElMismoTextoQueElResumen() {
        List<Bloqueo> bloqueos = List.of(new Bloqueo.LoteEnCurso("2026-15"), new Bloqueo.LoteEnCurso("2026-16"));

        EliminacionBloqueadaException e = new EliminacionBloqueadaException(bloqueos);

        assertEquals(bloqueos, e.getBloqueos());
        assertEquals(Mensajes.ELIMINACION_BLOQUEADA + "\n" + TextoBloqueos.describir(bloqueos), e.getMessage());
    }

    /** Un lote en curso lo resuelve el operador finalizándolo: no es "otro se te adelantó". */
    @Test
    void excepcion_esDeNegocioYNoUnConflicto() {
        assertTrue(BusinessException.class.isAssignableFrom(EliminacionBloqueadaException.class));
        assertFalse(ConflictoConcurrenciaException.class.isAssignableFrom(EliminacionBloqueadaException.class));
    }

    @Test
    void excepcion_sinBloqueos_lanza() {
        assertThrows(IllegalArgumentException.class, () -> new EliminacionBloqueadaException(List.of()));
    }

    @Test
    void resumenEquipo_rechazaModuloLavadero() {
        assertThrows(IllegalArgumentException.class, () -> new ResumenEquipo(
            new IngresoAEliminar(ModuloIngreso.LAVADERO, 1), null, null, null, null, "Nuevo", 0,
            List.of(), List.of(), List.of()));
    }
}
