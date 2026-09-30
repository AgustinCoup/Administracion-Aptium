package com.example.common.eliminacion;

import com.example.common.constants.Constantes;
import com.example.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IngresoArchivadoTest {

    private static IngresoArchivado conMotivo(String motivo) {
        return new IngresoArchivado(ModuloIngreso.OTROS, 7, "Cliente", null, "Nuevo",
            motivo, "puesto", null, "{\"formato\":1}");
    }

    private static IngresoArchivado conSnapshot(String snapshot) {
        return new IngresoArchivado(ModuloIngreso.OTROS, 7, "Cliente", null, "Nuevo",
            "cargado dos veces", "puesto", null, snapshot);
    }

    @Test
    void motivoBlanco_lanza() {
        assertThrows(ValidationException.class, () -> conMotivo("   "));
        assertThrows(ValidationException.class, () -> conMotivo(null));
    }

    @Test
    void motivoDeMasDe500_lanza() {
        String largo = "x".repeat(Constantes.Eliminacion.MOTIVO_MAX_LARGO + 1);
        assertThrows(ValidationException.class, () -> conMotivo(largo));
    }

    @Test
    void motivoDeExactamente500_seAcepta() {
        String justo = "x".repeat(Constantes.Eliminacion.MOTIVO_MAX_LARGO);
        assertEquals(justo, conMotivo(justo).motivo());
    }

    /** Los espacios de los bordes no cuentan para el largo ni se archivan. */
    @Test
    void motivo_seGuardaSinEspaciosEnLosBordes() {
        assertEquals("cargado dos veces", conMotivo("  cargado dos veces \n").motivo());
    }

    @Test
    void snapshotVacio_lanza() {
        assertThrows(IllegalArgumentException.class, () -> conSnapshot(""));
        assertThrows(IllegalArgumentException.class, () -> conSnapshot(" "));
        assertThrows(IllegalArgumentException.class, () -> conSnapshot(null));
    }

    @Test
    void moduloNull_lanza() {
        assertThrows(NullPointerException.class, () -> new IngresoArchivado(null, 7, "C", null,
            "Nuevo", "motivo", "puesto", null, "{}"));
    }
}
