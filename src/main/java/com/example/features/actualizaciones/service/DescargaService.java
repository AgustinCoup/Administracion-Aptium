package com.example.features.actualizaciones.service;

import com.example.common.constants.Constantes;
import com.example.features.actualizaciones.exception.ActualizacionException;
import com.example.features.actualizaciones.model.ReleaseInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Descarga el fat JAR de un release a un directorio de staging que el usuario
 * siempre puede escribir ({@code %LOCALAPPDATA%/Aptium/updates}), y verifica su
 * integridad contra el checksum SHA-256 publicado junto al asset. Nunca escribe
 * en la ruta del JAR en ejecución — eso es responsabilidad de la Fase 3.
 */
public class DescargaService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int TAMANO_BUFFER = 8192;

    private final HttpClient httpClient;

    public DescargaService() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    public DescargaService(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClient no puede ser nulo");
        }
        this.httpClient = httpClient;
    }

    /**
     * @param release             release del cual descargar el asset del JAR y su checksum
     * @param onBytesDescargados  callback de progreso (bytes acumulados); puede ser {@code null}
     * @return ruta del JAR descargado y verificado, en el directorio de staging
     * @throws ActualizacionException si falta algún asset, falla la descarga o el checksum no coincide
     */
    public Path descargarYVerificar(ReleaseInfo release, Consumer<Long> onBytesDescargados) {
        if (release == null) {
            throw new IllegalArgumentException("ReleaseInfo no puede ser nulo");
        }
        String jarUrl = release.assets().get(Constantes.Actualizaciones.ASSET_JAR);
        String checksumUrl = release.assets().get(Constantes.Actualizaciones.ASSET_CHECKSUM);
        if (jarUrl == null || checksumUrl == null) {
            throw new ActualizacionException(
                "El release " + release.tag() + " no tiene los assets esperados ("
                    + Constantes.Actualizaciones.ASSET_JAR + " / " + Constantes.Actualizaciones.ASSET_CHECKSUM + ")");
        }

        Path directorioStaging = resolverDirectorioStaging();
        Path archivoParcial = directorioStaging.resolve("aptium-" + release.tag() + ".jar.part");
        Path archivoFinal = directorioStaging.resolve("aptium-" + release.tag() + ".jar");

        String hashEsperado = descargarChecksum(checksumUrl);
        descargarArchivo(jarUrl, archivoParcial, onBytesDescargados);

        String hashCalculado = calcularSha256(archivoParcial);
        if (!hashCalculado.equalsIgnoreCase(hashEsperado)) {
            eliminarSilenciosamente(archivoParcial);
            throw new ActualizacionException(
                "El checksum del JAR descargado no coincide con el esperado para el release " + release.tag());
        }

        try {
            Files.move(archivoParcial, archivoFinal, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            eliminarSilenciosamente(archivoParcial);
            throw new ActualizacionException("No se pudo finalizar la descarga del JAR verificado", e);
        }
        return archivoFinal;
    }

    private Path resolverDirectorioStaging() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            throw new ActualizacionException("La variable de entorno LOCALAPPDATA no está definida");
        }
        Path directorio = Path.of(localAppData, "Aptium", "updates");
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            throw new ActualizacionException("No se pudo crear el directorio de staging: " + directorio, e);
        }
        return directorio;
    }

    private String descargarChecksum(String checksumUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(checksumUrl)).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ActualizacionException("No se pudo descargar el checksum del release", e);
        }
        if (response.statusCode() != 200) {
            throw new ActualizacionException(
                "El servidor respondió con estado " + response.statusCode() + " al descargar el checksum");
        }
        String cuerpo = response.body().trim();
        int finHash = cuerpo.indexOf(' ');
        return (finHash > 0 ? cuerpo.substring(0, finHash) : cuerpo).trim();
    }

    private void descargarArchivo(String url, Path destino, Consumer<Long> onBytesDescargados) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ActualizacionException("No se pudo descargar el JAR del release", e);
        }
        if (response.statusCode() != 200) {
            eliminarSilenciosamente(destino);
            throw new ActualizacionException(
                "El servidor respondió con estado " + response.statusCode() + " al descargar el JAR");
        }

        long totalDescargado = 0;
        try (InputStream entrada = response.body(); OutputStream salida = Files.newOutputStream(destino)) {
            byte[] buffer = new byte[TAMANO_BUFFER];
            int leidos;
            while ((leidos = entrada.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
                totalDescargado += leidos;
                if (onBytesDescargados != null) {
                    onBytesDescargados.accept(totalDescargado);
                }
            }
        } catch (IOException e) {
            eliminarSilenciosamente(destino);
            throw new ActualizacionException("Error de I/O al descargar el JAR del release", e);
        }
    }

    private String calcularSha256(Path archivo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream entrada = Files.newInputStream(archivo)) {
                byte[] buffer = new byte[TAMANO_BUFFER];
                int leidos;
                while ((leidos = entrada.read(buffer)) != -1) {
                    digest.update(buffer, 0, leidos);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            eliminarSilenciosamente(archivo);
            throw new ActualizacionException("No se pudo calcular el checksum del archivo descargado", e);
        }
    }

    private void eliminarSilenciosamente(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignored) {
            // limpieza best-effort; no debe enmascarar la excepción original
        }
    }
}
