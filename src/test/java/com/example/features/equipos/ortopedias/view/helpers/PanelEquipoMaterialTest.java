package com.example.features.equipos.ortopedias.view.helpers;

import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.MaterialRegistrableInterface;
import com.example.features.equipos.ortopedias.model.EstadoEquipo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.ListSelectionModel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PanelEquipoMaterialTest {

    private PanelEquipoMaterial panel;
    private List<MaterialRegistrableInterface> materiales;

    @BeforeEach
    void setUp() {
        panel = new PanelEquipoMaterial("Equipos", "Materiales", true);
        materiales = List.of(
            material(), material(), material());
        EquipoRegistrableInterface equipo = mock(EquipoRegistrableInterface.class);
        when(equipo.calcularEstado()).thenReturn(EstadoEquipo.NUEVO);
        when(equipo.getMaterialesRegistrables()).thenReturn(materiales);
        panel.actualizarEquipos(List.of(equipo));
        panel.getTablaEquipos().setRowSelectionInterval(0, 0);
    }

    private static MaterialRegistrableInterface material() {
        MaterialRegistrableInterface m = mock(MaterialRegistrableInterface.class);
        when(m.getEstado()).thenReturn(EstadoEquipo.NUEVO);
        return m;
    }

    @Test
    void porDefecto_materialesEnSeleccionSimple() {
        assertEquals(ListSelectionModel.SINGLE_SELECTION,
            panel.getTablaMateriales().getSelectionModel().getSelectionMode());
    }

    @Test
    void habilitarSeleccionMultiple_ponerSeleccionMultiple() {
        panel.habilitarSeleccionMultipleMateriales(new JButton());

        assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
            panel.getTablaMateriales().getSelectionModel().getSelectionMode());
    }

    @Test
    void getMaterialesSeleccionados_devuelveLosObjetosDeLasFilasSeleccionadas() {
        panel.habilitarSeleccionMultipleMateriales();
        panel.getTablaMateriales().addRowSelectionInterval(0, 0);
        panel.getTablaMateriales().addRowSelectionInterval(2, 2);

        List<MaterialRegistrableInterface> seleccion = panel.getMaterialesSeleccionados();

        assertEquals(2, seleccion.size());
        assertSame(materiales.get(0), seleccion.get(0));
        assertSame(materiales.get(2), seleccion.get(1));
    }
}
