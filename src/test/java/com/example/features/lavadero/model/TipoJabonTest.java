package com.example.features.lavadero.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TipoJabonTest {

    @Test
    void roundTrip_name_convierteCorrectamente() {
        for (TipoJabon tipo : TipoJabon.values()) {
            assertSame(tipo, TipoJabon.valueOf(tipo.name()));
        }
    }

    @Test
    void toString_devuelveDisplayName() {
        for (TipoJabon tipo : TipoJabon.values()) {
            assertEquals(tipo.getDisplayName(), tipo.toString());
        }
    }

    @Test
    void displayName_noEsNullNiVacio() {
        for (TipoJabon tipo : TipoJabon.values()) {
            assertNotNull(tipo.getDisplayName());
            assertFalse(tipo.getDisplayName().isBlank());
        }
    }
}
