package com.example.features.equipos.view.helpers;

import com.example.features.instituciones.model.Institucion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre la regla que decide si el filtro del reporte usa la entidad elegida:
 * el id solo vale si el texto visible sigue coincidiendo con ella.
 */
class ImprimirEquiposDialogTest {

    private static final Institucion HOSPITAL_A = new Institucion(1, "Hospital A");

    @Test
    void sinSeleccion_retornaNull() {
        assertNull(ImprimirEquiposDialog.idSiCoincide(null, "Hospital A", Institucion::getId));
    }

    @Test
    void textoCoincideConLaSeleccion_retornaId() {
        assertEquals(1, ImprimirEquiposDialog.idSiCoincide(HOSPITAL_A, "Hospital A", Institucion::getId));
    }

    @Test
    void residuoDeDosCaracteres_retornaNull_noElIdAnterior() {
        // Regresión: AutocompleteListener no avisa por debajo de 3 caracteres, así que
        // "Ho" dejaba vivo el id de "Hospital A" y el reporte salía filtrado por él.
        assertNull(ImprimirEquiposDialog.idSiCoincide(HOSPITAL_A, "Ho", Institucion::getId));
    }

    @Test
    void campoVaciado_retornaNull() {
        assertNull(ImprimirEquiposDialog.idSiCoincide(HOSPITAL_A, "", Institucion::getId));
    }

    @Test
    void textoRetipeadoAOtraEntidadSinSeleccionarla_retornaNull() {
        assertNull(ImprimirEquiposDialog.idSiCoincide(HOSPITAL_A, "Hospital B", Institucion::getId));
    }

    @Test
    void textoConEspaciosAlrededor_igualCoincide() {
        assertEquals(1, ImprimirEquiposDialog.idSiCoincide(HOSPITAL_A, "  Hospital A  ", Institucion::getId));
    }
}
