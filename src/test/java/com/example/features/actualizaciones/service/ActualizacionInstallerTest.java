package com.example.features.actualizaciones.service;

import com.example.features.actualizaciones.exception.ActualizacionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * No ejercita el camino feliz completo (que termina en {@code System.exit(0)}): eso mataría
 * la JVM del test runner. Cubre la adquisición/liberación del lock de actualización, que es
 * la responsabilidad propia de esta clase antes de lanzar el script desacoplado.
 */
@ExtendWith(MockitoExtension.class)
class ActualizacionInstallerTest {

    @Mock RutaJarResolver rutaJarResolver;
    @Mock ScriptDeReemplazoGenerator scriptGenerator;

    private ActualizacionInstaller crearInstaller() {
        return new ActualizacionInstaller(rutaJarResolver, scriptGenerator);
    }

    @Test
    @DisplayName("rechaza instalar si ya hay un lock reciente de otra actualización en curso")
    void instalarYReiniciar_lockExistenteYReciente_lanzaExcepcionSinTocarElGenerador(@TempDir Path base) throws IOException {
        Path jarTarget = base.resolve("aptium.jar");
        Path lock = base.resolve("aptium.jar.lock");
        Files.createFile(lock);
        when(rutaJarResolver.resolverJarActual()).thenReturn(jarTarget);
        when(rutaJarResolver.resolverLockActualizacion()).thenReturn(lock);

        ActualizacionException ex = assertThrows(ActualizacionException.class,
            () -> crearInstaller().instalarYReiniciar(base.resolve("aptium-nuevo.jar")));

        assertTrue(ex.getMessage().contains("actualización en curso"));
        verifyNoInteractions(scriptGenerator);
        assertTrue(Files.exists(lock), "un lock ajeno reciente no debe borrarse");
    }

    @Test
    @DisplayName("recicla un lock obsoleto (de una corrida anterior que no lo limpió) y sigue adelante")
    void instalarYReiniciar_lockObsoleto_loReciclaYPropagaFalloDelGenerador(@TempDir Path base) throws IOException {
        Path jarTarget = base.resolve("aptium.jar");
        Path lock = base.resolve("aptium.jar.lock");
        Files.createFile(lock);
        Files.setLastModifiedTime(lock, FileTime.from(Instant.now().minus(30, ChronoUnit.MINUTES)));
        when(rutaJarResolver.resolverJarActual()).thenReturn(jarTarget);
        when(rutaJarResolver.resolverLockActualizacion()).thenReturn(lock);
        when(scriptGenerator.generar(anyLong(), any(), eq(jarTarget), any(), eq(lock)))
            .thenThrow(new ActualizacionException("fallo simulado del generador"));

        assertThrows(ActualizacionException.class,
            () -> crearInstaller().instalarYReiniciar(base.resolve("aptium-nuevo.jar")));

        verify(scriptGenerator).generar(anyLong(), any(), eq(jarTarget), any(), eq(lock));
        assertFalse(Files.exists(lock), "si el generador falla después, el lock reciclado debe liberarse igual");
    }

    @Test
    @DisplayName("si el generador de script falla, libera el lock que había tomado")
    void instalarYReiniciar_generadorFalla_liberaElLockPropio(@TempDir Path base) {
        Path jarTarget = base.resolve("aptium.jar");
        Path lock = base.resolve("aptium.jar.lock");
        when(rutaJarResolver.resolverJarActual()).thenReturn(jarTarget);
        when(rutaJarResolver.resolverLockActualizacion()).thenReturn(lock);
        when(scriptGenerator.generar(anyLong(), any(), eq(jarTarget), any(), eq(lock)))
            .thenThrow(new ActualizacionException("fallo simulado del generador"));

        assertThrows(ActualizacionException.class,
            () -> crearInstaller().instalarYReiniciar(base.resolve("aptium-nuevo.jar")));

        assertFalse(Files.exists(lock), "el lock que esta corrida creó debe liberarse si no llega a lanzar el script");
    }

    @Test
    @DisplayName("rechaza jarVerificado nulo")
    void instalarYReiniciar_jarVerificadoNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> crearInstaller().instalarYReiniciar(null));
    }

    @Test
    @DisplayName("rechaza constructor con dependencias nulas")
    void constructor_dependenciasNulas_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new ActualizacionInstaller(null, scriptGenerator));
        assertThrows(IllegalArgumentException.class, () -> new ActualizacionInstaller(rutaJarResolver, null));
    }
}
