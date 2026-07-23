package com.example.features.actualizaciones.service;

import com.example.common.constants.Constantes;
import com.example.features.actualizaciones.exception.ActualizacionException;
import com.example.features.actualizaciones.model.ReleaseInfo;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class DescargaServiceTest {

    private static final byte[] CONTENIDO_JAR = "contenido-de-prueba-del-jar".repeat(50).getBytes(StandardCharsets.UTF_8);

    private HttpServer servidor;
    private Path archivoDescargado;

    private final DescargaService descargaService = new DescargaService(HttpClient.newHttpClient());

    @AfterEach
    void tearDown() throws IOException {
        if (servidor != null) {
            servidor.stop(0);
        }
        if (archivoDescargado != null) {
            Files.deleteIfExists(archivoDescargado);
        }
    }

    private String sha256Hex(byte[] datos) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpServer iniciarServidor(byte[] contenidoJar, String checksumBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicLong bytesServidos = new AtomicLong();
        server.createContext("/jar", exchange -> {
            exchange.sendResponseHeaders(200, contenidoJar.length);
            exchange.getResponseBody().write(contenidoJar);
            exchange.getResponseBody().close();
            bytesServidos.addAndGet(contenidoJar.length);
        });
        server.createContext("/checksum", exchange -> {
            byte[] body = checksumBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        return server;
    }

    private String urlDe(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    void descargarYVerificar_checksumCoincide_devuelvePathConContenidoEsperado() throws IOException {
        String hashCorrecto = sha256Hex(CONTENIDO_JAR);
        servidor = iniciarServidor(CONTENIDO_JAR, hashCorrecto + "  " + Constantes.Actualizaciones.ASSET_JAR);
        String tag = "vTEST-" + UUID.randomUUID();
        ReleaseInfo release = new ReleaseInfo(
            tag,
            Map.of(
                Constantes.Actualizaciones.ASSET_JAR, urlDe(servidor, "/jar"),
                Constantes.Actualizaciones.ASSET_CHECKSUM, urlDe(servidor, "/checksum")),
            "changelog");

        AtomicLong ultimoProgreso = new AtomicLong();
        Path resultado = descargaService.descargarYVerificar(release, ultimoProgreso::set);
        archivoDescargado = resultado;

        assertTrue(Files.exists(resultado));
        assertArrayEquals(CONTENIDO_JAR, Files.readAllBytes(resultado));
        assertEquals(CONTENIDO_JAR.length, ultimoProgreso.get());
        assertFalse(Files.exists(Path.of(resultado.toString() + ".part")));
    }

    @Test
    void descargarYVerificar_checksumNoCoincide_lanzaExcepcionYNoDejaArchivoFinal() throws IOException {
        servidor = iniciarServidor(CONTENIDO_JAR, "0000000000000000000000000000000000000000000000000000000000000000  " + Constantes.Actualizaciones.ASSET_JAR);
        String tag = "vTEST-" + UUID.randomUUID();
        ReleaseInfo release = new ReleaseInfo(
            tag,
            Map.of(
                Constantes.Actualizaciones.ASSET_JAR, urlDe(servidor, "/jar"),
                Constantes.Actualizaciones.ASSET_CHECKSUM, urlDe(servidor, "/checksum")),
            "changelog");

        assertThrows(ActualizacionException.class, () -> descargaService.descargarYVerificar(release, null));

        String localAppData = System.getenv("LOCALAPPDATA");
        Path directorioStaging = Path.of(localAppData, "Aptium", "updates");
        Path archivoFinal = directorioStaging.resolve("aptium-" + tag + ".jar");
        Path archivoParcial = directorioStaging.resolve("aptium-" + tag + ".jar.part");
        assertFalse(Files.exists(archivoFinal));
        assertFalse(Files.exists(archivoParcial));
    }

    @Test
    void descargarYVerificar_assetFaltante_lanzaExcepcionSinIntentarDescarga() {
        ReleaseInfo release = new ReleaseInfo(
            "vTEST-" + UUID.randomUUID(),
            Map.of(Constantes.Actualizaciones.ASSET_JAR, "http://127.0.0.1:1/jar"),
            "changelog");

        assertThrows(ActualizacionException.class, () -> descargaService.descargarYVerificar(release, null));
    }

    @Test
    void constructor_httpClientNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new DescargaService(null));
    }
}
