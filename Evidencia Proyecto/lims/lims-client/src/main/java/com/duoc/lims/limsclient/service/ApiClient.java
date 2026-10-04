package com.duoc.lims.limsclient.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.duoc.lims.limsclient.model.LoginRequest;
import com.duoc.lims.limsclient.model.Muestra;
import com.duoc.lims.limsclient.model.MuestraRequest;
import com.duoc.lims.limsclient.model.SolicitudResetRequest;
import com.duoc.lims.limsclient.model.UsuarioRequest;
import com.duoc.lims.limsclient.model.UsuarioResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ApiClient {

    private static final String BASE_URL = "http://localhost:8080/api";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<Muestra> listarMuestras() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/muestras"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Error al listar muestras: HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Muestra.class));
    }

    public Muestra crearMuestra(MuestraRequest datos) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(datos);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/muestras"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException("Error al crear muestra: HTTP " + response.statusCode() + " - " + response.body());
        }
        return objectMapper.readValue(response.body(), Muestra.class);
    }

    public UsuarioResponse login(LoginRequest datos) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(datos);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/usuarios/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
        return objectMapper.readValue(response.body(), UsuarioResponse.class);
    }

    public UsuarioResponse crearUsuario(UsuarioRequest datos) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(datos);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/usuarios"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException(response.body());
        }
        return objectMapper.readValue(response.body(), UsuarioResponse.class);
    }

    public void solicitarResetPassword(String username) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(new SolicitudResetRequest(username));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/usuarios/solicitar-reset"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
    }
}