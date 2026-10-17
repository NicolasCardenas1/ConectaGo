package com.duoc.lims.limsclient.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.duoc.lims.limsclient.Navigator;
import com.duoc.lims.limsclient.model.LoginRequest;
import com.duoc.lims.limsclient.model.Investigador;
import com.duoc.lims.limsclient.model.InvestigadorRequest;
import com.duoc.lims.limsclient.model.CustodiaRequest;
import com.duoc.lims.limsclient.model.AnalisisCatalogoRequest;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.duoc.lims.limsclient.model.LoginResponse;
import com.duoc.lims.limsclient.model.ResetPasswordRequest;
import javafx.application.Platform;
import com.duoc.lims.limsclient.model.Muestra;
import com.duoc.lims.limsclient.model.MuestraRequest;
import com.duoc.lims.limsclient.model.SolicitudResetRequest;
import com.duoc.lims.limsclient.model.UsuarioRequest;
import com.duoc.lims.limsclient.model.UsuarioResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.duoc.lims.limsclient.model.AnalisisCatalogo;
import com.duoc.lims.limsclient.model.AsignarAnalisisRequest;
import com.duoc.lims.limsclient.model.MuestraDetalle;

import com.duoc.lims.limsclient.model.IngresoResultadoRequest;
import com.duoc.lims.limsclient.model.AprobacionRequest;
import com.duoc.lims.limsclient.model.ResultadoPendiente;

public class ApiClient {

    private static final String BASE_URL = "http://localhost:8080/api";

    /**
     * RF06: token de la sesión actual. Es static porque cada pantalla crea su propio ApiClient,
     * pero todas comparten la misma sesión.
     */
    private static volatile String token;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);   // fechas como "2026-10-17T10:30:00"

    public List<Muestra> listarMuestras() throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/muestras"))
                .GET()
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Error al listar muestras: HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Muestra.class));
    }

    public Muestra crearMuestra(MuestraRequest datos) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(datos);

        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/muestras"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException("Error al crear muestra: HTTP " + response.statusCode() + " - " + response.body());
        }
        return objectMapper.readValue(response.body(), Muestra.class);
    }

    public LoginResponse login(LoginRequest datos) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(datos);

        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/usuarios/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
        LoginResponse sesion = objectMapper.readValue(response.body(), LoginResponse.class);
        token = sesion.token();   // desde ahora todas las peticiones llevan el token
        return sesion;
    }

    /** Registro público desde el login: el backend siempre crea la cuenta como Analista. */
    public UsuarioResponse registrarse(UsuarioRequest datos) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(datos);

        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/usuarios/registro"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException(response.body());
        }
        return objectMapper.readValue(response.body(), UsuarioResponse.class);
    }

    public void solicitarResetPassword(String username) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(new SolicitudResetRequest(username));

        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/usuarios/solicitar-reset"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
    }

    public MuestraDetalle obtenerDetalleMuestra(int idMuestra) throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/muestras/" + idMuestra))
                .GET()
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
        return objectMapper.readValue(response.body(), MuestraDetalle.class);
    }

    public List<AnalisisCatalogo> listarAnalisisCatalogo() throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/analisis"))
                .GET()
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Error al listar análisis: HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AnalisisCatalogo.class));
    }

    public void asignarAnalisis(int idMuestra, int idAnalisis) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(new AsignarAnalisisRequest(idMuestra, idAnalisis));

        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/muestra-analisis"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException(response.body());   // ej: "Ese análisis ya está asignado a la muestra"
        }
    }

    public void ingresarResultado(IngresoResultadoRequest datos) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(datos);

        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/resultados"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException(response.body());   // ej: "debe justificarlo en 'observaciones'"
        }
    }

    public List<ResultadoPendiente> listarResultadosPendientes() throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/resultados?pendientes=true"))
                .GET()
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Error al listar resultados pendientes: HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ResultadoPendiente.class));
    }

    public void evaluarResultado(AprobacionRequest datos) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(datos);

        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/aprobaciones"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException(response.body());   // ej: "Un rechazo debe incluir un comentario..."
        }
    }

    /** RF05: emite el informe PDF de una muestra aprobada y devuelve el archivo. */
    public byte[] generarReporte(int idMuestra) throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/muestras/" + idMuestra + "/reporte"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<byte[]> response = enviar(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            // ej: "Solo se puede emitir el informe de una muestra Aprobada"
            throw new IOException(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return response.body();
    }

    // =====================================================================
    // RF06 — sesión
    // =====================================================================

    public static boolean haySesion() {
        return token != null;
    }

    public static void olvidarSesion() {
        token = null;
    }

    public static String tokenActual() {
        return token;
    }

    /** Cierra en el backend la sesión del token indicado (si falla, la sesión expirará sola). */
    public void cerrarSesion(String actual) {
        if (actual == null) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/usuarios/logout"))
                    .header("Authorization", "Bearer " + actual)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignorada) {
            // Si el backend no responde, la sesión expirará sola.
        }
    }

    /** Crea la petición agregando el token de sesión, si existe. */
    private HttpRequest.Builder nuevaPeticion() {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    /**
     * Envía la petición y maneja los errores de seguridad de forma central:
     * 401 = la sesión expiró (vuelve al login) · 403 = el rol no tiene permiso.
     */
    private <T> HttpResponse<T> enviar(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        HttpResponse<T> response = httpClient.send(request, handler);
        if (response.statusCode() == 401 && token != null) {
            token = null;
            Platform.runLater(Navigator::irALoginPorSesionExpirada);
            throw new IOException("Tu sesión expiró. Inicia sesión nuevamente.");
        }
        if (response.statusCode() == 403) {
            throw new IOException("No tienes permisos para realizar esta acción.");
        }
        return response;
    }

    // =====================================================================
    // RF06 — administración de usuarios (solo Administrador)
    // =====================================================================

    public List<UsuarioResponse> listarUsuarios() throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/usuarios"))
                .GET()
                .build();
        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, UsuarioResponse.class));
    }

    /** El Administrador crea un usuario con el rol que elija. */
    public UsuarioResponse crearUsuarioConRol(UsuarioRequest datos) throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/usuarios"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(datos)))
                .build();
        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IOException(response.body());
        }
        return objectMapper.readValue(response.body(), UsuarioResponse.class);
    }

    public void resetearPassword(int idUsuario, String nuevaPassword) throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/usuarios/" + idUsuario + "/resetear-password"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(new ResetPasswordRequest(nuevaPassword))))
                .build();
        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
    }

    public void cambiarEstadoUsuario(int idUsuario, boolean activo) throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/usuarios/" + idUsuario + "/estado?activo=" + activo))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
    }

    // =====================================================================
    // v4 — Investigadores, cadena de custodia y catálogo de parámetros
    // =====================================================================

    /** Helper: envía un JSON y valida el código esperado. Devuelve el cuerpo de la respuesta. */
    private String enviarJson(String metodo, String ruta, Object cuerpo, int codigoEsperado)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = nuevaPeticion().uri(URI.create(BASE_URL + ruta));
        if (cuerpo == null) {
            b.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json")
             .method(metodo, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(cuerpo)));
        }
        HttpResponse<String> response = enviar(b.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != codigoEsperado) {
            throw new IOException(response.body());
        }
        return response.body();
    }

    public List<Investigador> listarInvestigadores(boolean soloActivos) throws IOException, InterruptedException {
        String json = enviarJson("GET", "/investigadores?activos=" + soloActivos, null, 200);
        return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Investigador.class));
    }

    public Investigador crearInvestigador(InvestigadorRequest datos) throws IOException, InterruptedException {
        return objectMapper.readValue(enviarJson("POST", "/investigadores", datos, 201), Investigador.class);
    }

    public Investigador actualizarInvestigador(int id, InvestigadorRequest datos) throws IOException, InterruptedException {
        return objectMapper.readValue(enviarJson("PUT", "/investigadores/" + id, datos, 200), Investigador.class);
    }

    public void cambiarEstadoInvestigador(int id, boolean activo) throws IOException, InterruptedException {
        enviarJson("PATCH", "/investigadores/" + id + "/estado?activo=" + activo, null, 200);
    }

    /** Firma un evento de custodia (quien recibe = usuario de la sesión). */
    public void registrarCustodia(int idMuestra, CustodiaRequest datos) throws IOException, InterruptedException {
        enviarJson("POST", "/muestras/" + idMuestra + "/custodia", datos, 201);
    }

    public void crearAnalisis(AnalisisCatalogoRequest datos) throws IOException, InterruptedException {
        enviarJson("POST", "/analisis", datos, 201);
    }

    public void actualizarAnalisis(int id, AnalisisCatalogoRequest datos) throws IOException, InterruptedException {
        enviarJson("PUT", "/analisis/" + id, datos, 200);
    }

    public void cambiarEstadoAnalisis(int id, boolean activo) throws IOException, InterruptedException {
        enviarJson("PATCH", "/analisis/" + id + "/estado?activo=" + activo, null, 200);
    }
}
