package com.example.features.equipos.ortopedias.view.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import com.example.features.equipos.ortopedias.model.Material;
import com.example.features.equipos.otros.model.EquipoOtros;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquipoTableModelTest {

    private static Equipo ortopedia(int id, EstadoEquipo estado) {
        Equipo e = new Equipo();
        e.setId(id);
        e.agregarMaterial(new Material(id, 400, "Tornillo", 5, estado));
        return e;
    }

    @Test
    @DisplayName("reemplazarEquipo cambia la fila del mismo tipo e id, sin reordenar, y actualiza su estado")
    void reemplazarEquipo_sinReordenar_actualizaElEstado() {
        EquipoTableModel modelo = new EquipoTableModel(new String[]{"Cliente", "Institución", "Estado"});
        Equipo nuevo  = ortopedia(1, EstadoEquipo.NUEVO);
        EquipoOtros otroMismoId = new EquipoOtros();
        otroMismoId.setId(1);
        Equipo lavado = ortopedia(2, EstadoEquipo.LAVADO);
        modelo.actualizarDatos(List.of(nuevo, otroMismoId, lavado));

        Equipo copia = nuevo.copiarParaPreview();
        copia.aplicarMovimientoPreview(copia.getMateriales().get(0), 5, EstadoEquipo.EMPAQUETADO);
        modelo.reemplazarEquipo(copia);

        assertSame(copia, modelo.getEquipoAt(0));
        assertSame(otroMismoId, modelo.getEquipoAt(1));
        assertSame(lavado, modelo.getEquipoAt(2));
        assertEquals(EstadoEquipo.EMPAQUETADO.getNombre(), modelo.getValueAt(0, 2));
    }
}
