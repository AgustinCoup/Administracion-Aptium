package com.example.features.actualizaciones.service;

import com.example.common.constants.Constantes;
import com.example.features.actualizaciones.exception.ActualizacionException;
import com.example.features.actualizaciones.model.ReleaseInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Consulta el último release publicado en GitHub Releases.
 * Único colaborador que pega a la red real — todo lo demás del feature se testea
 * detrás de {@link IReleaseRepository}.
 */
public class GithubReleaseClient implements IReleaseRepository {

    private static final String URL_TEMPLATE = "https://api.github.com/repos/%s/%s/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public GithubReleaseClient() {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    public GithubReleaseClient(HttpClient httpClient) {
        if (httpClient == null) {
            throw new IllegalArgumentException("HttpClient no puede ser nulo");
        }
        this.httpClient = httpClient;
    }

    @Override
    public ReleaseInfo obtenerUltimoRelease() {
        String url = String.format(
            URL_TEMPLATE, Constantes.Actualizaciones.GITHUB_OWNER, Constantes.Actualizaciones.GITHUB_REPO);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/vnd.github+json")
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ActualizacionException("No se pudo consultar el último release de GitHub", e);
        }

        if (response.statusCode() != 200) {
            throw new ActualizacionException(
                "GitHub respondió con estado " + response.statusCode() + " al consultar el último release");
        }

        return parsear(response.body());
    }

    private ReleaseInfo parsear(String body) {
        try {
            JSONObject json = new JSONObject(body);
            String tag = json.getString("tag_name");
            String changelog = json.optString("body", "");

            Map<String, String> assets = new HashMap<>();
            JSONArray assetsJson = json.optJSONArray("assets");
            if (assetsJson != null) {
                for (int i = 0; i < assetsJson.length(); i++) {
                    JSONObject asset = assetsJson.getJSONObject(i);
                    assets.put(asset.getString("name"), asset.getString("browser_download_url"));
                }
            }
            return new ReleaseInfo(tag, assets, changelog);
        } catch (JSONException e) {
            throw new ActualizacionException("Respuesta inesperada de GitHub al consultar el último release", e);
        }
    }
}
