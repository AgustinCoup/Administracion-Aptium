package com.example.common.eliminacion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PuestoDeTrabajoTest {

    @Test
    void actual_nuncaLanzaYEmpiezaConElUsuario() {
        String puesto = assertDoesNotThrow(PuestoDeTrabajo::actual);

        assertTrue(puesto.startsWith(System.getProperty("user.name")),
            "el puesto empieza con el usuario del sistema operativo: " + puesto);
        assertTrue(puesto.length() <= PuestoDeTrabajo.LARGO_MAXIMO,
            "entra en la columna puesto de ingresos_eliminados");
    }
}
