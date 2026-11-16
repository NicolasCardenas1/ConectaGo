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
import com.duoc.lims.limsclient.model.ReporteResumen;
import com.duoc.lims.limsclient.model.Anomalia;
import com.duoc.lims.limsclient.model.CorreccionResultadoRequest;
import com.duoc.lims.limsclient.model.Dashboard;
import com.duoc.lims.limsclient.model.Equipo;
import com.duoc.lims.limsclient.model.EquipoRequest;
import com.duoc.lims.limsclient.model.Interpretacion;
import com.duoc.lims.limsclient.model.InterpretacionRequest;
import com.duoc.lims.limsclient.model.ResultadoVersion;
import com.duoc.lims.limsclient.model.Sitio;
import com.duoc.lims.limsclient.model.SitioRequest;
import com.duoc.lims.limsclient.model.TrazabilidadEvento;
import com.duoc.lims.limsclient.model.Ubicacion;
import com.duoc.lims.limsclient.model.UbicacionRequest;

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

    // =====================================================================
    // RF05 — historial de informes emitidos (Supervisor / Administrador)
    // =====================================================================

    public List<ReporteResumen> listarReportes() throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/reportes"))
                .GET()
                .build();
        HttpResponse<String> response = enviar(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(response.body());
        }
        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ReporteResumen.class));
    }

    // =====================================================================
    // v5 — Sitios, equipos, ubicaciones, trazabilidad, anomalías, IA y panel de inicio
    // =====================================================================

    private <T> List<T> lista(String json, Class<T> tipo) throws IOException {
        return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, tipo));
    }

    public List<Sitio> listarSitios(boolean soloActivos) throws IOException, InterruptedException {
        return lista(enviarJson("GET", "/sitios?activos=" + soloActivos, null, 200), Sitio.class);
    }

    public Sitio crearSitio(SitioRequest datos) throws IOException, InterruptedException {
        return objectMapper.readValue(enviarJson("POST", "/sitios", datos, 201), Sitio.class);
    }

    public void actualizarSitio(int id, SitioRequest datos) throws IOException, InterruptedException {
        enviarJson("PUT", "/sitios/" + id, datos, 200);
    }

    public void cambiarEstadoSitio(int id, boolean activo) throws IOException, InterruptedException {
        enviarJson("PATCH", "/sitios/" + id + "/estado?activo=" + activo, null, 200);
    }

    public List<Equipo> listarEquipos(boolean soloActivos) throws IOException, InterruptedException {
        return lista(enviarJson("GET", "/equipos?activos=" + soloActivos, null, 200), Equipo.class);
    }

    public void crearEquipo(EquipoRequest datos) throws IOException, InterruptedException {
        enviarJson("POST", "/equipos", datos, 201);
    }

    public void actualizarEquipo(int id, EquipoRequest datos) throws IOException, InterruptedException {
        enviarJson("PUT", "/equipos/" + id, datos, 200);
    }

    public void cambiarEstadoEquipo(int id, boolean activo) throws IOException, InterruptedException {
        enviarJson("PATCH", "/equipos/" + id + "/estado?activo=" + activo, null, 200);
    }

    public List<Ubicacion> listarUbicaciones(boolean soloActivas) throws IOException, InterruptedException {
        return lista(enviarJson("GET", "/ubicaciones?activas=" + soloActivas, null, 200), Ubicacion.class);
    }

    public void crearUbicacion(UbicacionRequest datos) throws IOException, InterruptedException {
        enviarJson("POST", "/ubicaciones", datos, 201);
    }

    public void actualizarUbicacion(int id, UbicacionRequest datos) throws IOException, InterruptedException {
        enviarJson("PUT", "/ubicaciones/" + id, datos, 200);
    }

    public void cambiarEstadoUbicacion(int id, boolean activo) throws IOException, InterruptedException {
        enviarJson("PATCH", "/ubicaciones/" + id + "/estado?activo=" + activo, null, 200);
    }

    /** Vista previa de la detección de anomalías mientras se escribe el valor (no guarda nada). */
    public Anomalia evaluarAnomalia(int idMuestraAnalisis, java.math.BigDecimal valor, Integer idResultado)
            throws IOException, InterruptedException {
        String ruta = "/resultados/anomalia?idMuestraAnalisis=" + idMuestraAnalisis + "&valor=" + valor.toPlainString()
                + (idResultado == null ? "" : "&idResultado=" + idResultado);
        return objectMapper.readValue(enviarJson("GET", ruta, null, 200), Anomalia.class);
    }

    /** Corrige un resultado: el backend crea una versión nueva y conserva la anterior. */
    public void corregirResultado(int idResultado, CorreccionResultadoRequest datos) throws IOException, InterruptedException {
        enviarJson("PUT", "/resultados/" + idResultado, datos, 200);
    }

    public List<ResultadoVersion> versionesResultado(int idResultado) throws IOException, InterruptedException {
        return lista(enviarJson("GET", "/resultados/" + idResultado + "/versiones", null, 200), ResultadoVersion.class);
    }

    public List<TrazabilidadEvento> trazabilidad(int idMuestra) throws IOException, InterruptedException {
        return lista(enviarJson("GET", "/muestras/" + idMuestra + "/trazabilidad", null, 200), TrazabilidadEvento.class);
    }

    public Interpretacion borradorInterpretacion(int idMuestra) throws IOException, InterruptedException {
        return objectMapper.readValue(enviarJson("POST", "/muestras/" + idMuestra + "/interpretacion/borrador", null, 200),
                Interpretacion.class);
    }

    public void guardarInterpretacion(int idMuestra, InterpretacionRequest datos) throws IOException, InterruptedException {
        enviarJson("PUT", "/muestras/" + idMuestra + "/interpretacion", datos, 204);
    }

    public Dashboard dashboard() throws IOException, InterruptedException {
        return objectMapper.readValue(enviarJson("GET", "/dashboard", null, 200), Dashboard.class);
    }

    public Dashboard.Tendencia tendencia(int idAnalisis, Integer idSitio) throws IOException, InterruptedException {
        String ruta = "/dashboard/tendencia?idAnalisis=" + idAnalisis + (idSitio == null ? "" : "&idSitio=" + idSitio);
        return objectMapper.readValue(enviarJson("GET", ruta, null, 200), Dashboard.Tendencia.class);
    }

    /** Re-descarga el PDF ya guardado de un informe (no genera uno nuevo). */
    public byte[] descargarReportePdf(int idReporte) throws IOException, InterruptedException {
        HttpRequest request = nuevaPeticion()
                .uri(URI.create(BASE_URL + "/reportes/" + idReporte + "/pdf"))
                .GET()
                .build();
        HttpResponse<byte[]> response = enviar(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return response.body();
    }
}
