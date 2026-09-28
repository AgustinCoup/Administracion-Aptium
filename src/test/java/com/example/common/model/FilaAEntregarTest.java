package com.example.common.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilaAEntregarTest {

    @Test
    void cantidadVistaNoPositiva_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new FilaAEntregar(1, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new FilaAEntregar(1, 10, -1));
    }

    /** El orden es lo que evita el deadlock entre dos entregas cruzadas, y H2 no lo delata. */
    @Test
    void ordenDeEscritura_ordenaPorEquipoYDespuesPorMaterial() {
        FilaAEntregar e2m1 = new FilaAEntregar(2, 1, 1);
        FilaAEntregar e1m9 = new FilaAEntregar(1, 9, 1);
        FilaAEntregar e1m3 = new FilaAEntregar(1, 3, 1);

        List<FilaAEntregar> ordenadas = List.of(e2m1, e1m9, e1m3).stream()
            .sorted(FilaAEntregar.ORDEN_DE_ESCRITURA)
            .toList();

        assertEquals(List.of(e1m3, e1m9, e2m1), ordenadas);
    }
}
