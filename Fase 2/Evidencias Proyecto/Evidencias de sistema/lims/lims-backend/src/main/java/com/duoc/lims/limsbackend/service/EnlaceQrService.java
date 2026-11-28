package com.duoc.lims.limsbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Locale;

/**
 * v9 — Enlace que va dentro del código QR de la etiqueta.
 *
 * El QR abre en el celular una página de solo lectura con el estado actual de la muestra
 * ({@code /m/{codigo}?k=firma}). La firma es un HMAC-SHA256 del código con una clave secreta del
 * servidor: sin ella no se puede ver una muestra adivinando su código (M-2026-0001, 0002, ...).
 *
 * La clave se toma de {@code lims.qr.secreto}; si no está configurada, se crea una al azar en
 * {@code firma/secreto-qr.txt} (fuera de git) y se reutiliza, para que las etiquetas ya impresas sigan
 * funcionando después de reiniciar.
 *
 * La dirección del servidor, en este orden:
 * <ol>
 *   <li>{@code lims.url-publica} (ej. https://lims.midominio.cl), si está configurada.</li>
 *   <li>El archivo {@code firma/url-publica.txt}, que escribe el script {@code iniciar-tunel-qr.ps1} con
 *       la dirección del túnel de Cloudflare: así cualquier celular, en cualquier red, abre el enlace.</li>
 *   <li>La IP de este PC en la red local (solo celulares en la misma red).</li>
 * </ol>
 * Se vuelve a leer cada 30 segundos, sin reiniciar el backend.
 */
@Service
public class EnlaceQrService {

    private static final Logger log = LoggerFactory.getLogger(EnlaceQrService.class);
    private static final int LARGO_FIRMA = 16;   // caracteres hexadecimales (64 bits)

    private final String urlConfigurada;
    private final String secretoConfigurado;
    private final Path archivoSecreto;
    private final Path archivoUrl;
    private final int puerto;

    private byte[] secreto;
    private String urlBase;
    private long urlLeidaEn;

    public EnlaceQrService(@Value("${lims.url-publica:}") String urlConfigurada,
                           @Value("${lims.qr.secreto:}") String secretoConfigurado,
                           @Value("${lims.qr.archivo-secreto:firma/secreto-qr.txt}") String archivoSecreto,
                           @Value("${lims.qr.archivo-url:firma/url-publica.txt}") String archivoUrl,
                           @Value("${server.port:8080}") int puerto) {
        this.urlConfigurada = urlConfigurada.trim();
        this.secretoConfigurado = secretoConfigurado.trim();
        this.archivoSecreto = Paths.get(archivoSecreto);
        this.archivoUrl = Paths.get(archivoUrl);
        this.puerto = puerto;
    }

    /** Enlace completo para el QR de la muestra. */
    public String enlace(String codigoMuestra) {
        return urlBase() + "/m/" + URLEncoder.encode(codigoMuestra, StandardCharsets.UTF_8)
                + "?k=" + firma(codigoMuestra);
    }

    /** true si la firma corresponde al código (comparación en tiempo constante). */
    public boolean firmaValida(String codigoMuestra, String firma) {
        if (codigoMuestra == null || firma == null) {
            return false;
        }
        byte[] esperada = firma(codigoMuestra).getBytes(StandardCharsets.US_ASCII);
        byte[] recibida = firma.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(esperada, recibida);
    }

    String firma(String codigoMuestra) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto(), "HmacSHA256"));
            byte[] h = mac.doFinal(codigoMuestra.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, LARGO_FIRMA);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el enlace del QR", e);
        }
    }

    private synchronized byte[] secreto() {
        if (secreto != null) {
            return secreto;
        }
        if (!secretoConfigurado.isEmpty()) {
            secreto = secretoConfigurado.getBytes(StandardCharsets.UTF_8);
            return secreto;
        }
        try {
            if (Files.exists(archivoSecreto)) {
                secreto = Files.readString(archivoSecreto, StandardCharsets.UTF_8).trim().getBytes(StandardCharsets.UTF_8);
            } else {
                byte[] azar = new byte[32];
                new SecureRandom().nextBytes(azar);
                String hex = HexFormat.of().formatHex(azar);
                if (archivoSecreto.getParent() != null) {
                    Files.createDirectories(archivoSecreto.getParent());
                }
                Files.writeString(archivoSecreto, hex, StandardCharsets.UTF_8);
                log.info("Clave de los enlaces QR creada en {}", archivoSecreto.toAbsolutePath());
                secreto = hex.getBytes(StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer ni crear " + archivoSecreto + ": " + e.getMessage(), e);
        }
        return secreto;
    }

    /**
     * Si no hay URL configurada, la IP se vuelve a leer cada 30 segundos: así el enlace se ajusta solo
     * cuando el PC cambia de red (otro Wi-Fi, punto de acceso del celular) sin reiniciar el backend.
     */
    private synchronized String urlBase() {
        if (!urlConfigurada.isEmpty()) {
            return urlConfigurada.replaceAll("/+$", "");
        }
        long ahora = System.currentTimeMillis();
        if (urlBase == null || ahora - urlLeidaEn > 30_000) {
            String tunel = urlDelTunel();
            String nueva = tunel != null ? tunel : "http://" + ipRedLocal() + ":" + puerto;
            if (!nueva.equals(urlBase)) {
                log.info("Enlaces QR con {}: {}", tunel != null ? "el túnel público" : "la IP de la red local", nueva);
            }
            urlBase = nueva;
            urlLeidaEn = ahora;
        }
        return urlBase;
    }

    /** Dirección pública del túnel, si el script del túnel está corriendo (null si no). */
    private String urlDelTunel() {
        try {
            if (Files.exists(archivoUrl)) {
                String url = Files.readString(archivoUrl, StandardCharsets.UTF_8).trim().replaceAll("/+$", "");
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    return url;
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo leer {}: {}", archivoUrl, e.getMessage());
        }
        return null;
    }

    /**
     * IPv4 de este PC en la red local (Wi-Fi o cable). Se ignoran los adaptadores virtuales de Docker,
     * WSL, Hyper-V, VirtualBox y VMware, porque el celular no puede llegar a esas direcciones.
     */
    static String ipRedLocal() {
        String mejor = null;
        int mejorPuntaje = -1;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || esVirtual(ni)) {
                    continue;
                }
                for (InetAddress dir : Collections.list(ni.getInetAddresses())) {
                    if (!(dir instanceof Inet4Address) || !dir.isSiteLocalAddress()) {
                        continue;
                    }
                    String ip = dir.getHostAddress();
                    int puntaje = ip.startsWith("192.168.") ? 3 : ip.startsWith("10.") ? 2 : 1;
                    if (puntaje > mejorPuntaje) {
                        mejor = ip;
                        mejorPuntaje = puntaje;
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("No se pudo leer la IP de la red local: {}", e.getMessage());
        }
        return mejor == null ? "localhost" : mejor;
    }

    private static boolean esVirtual(NetworkInterface ni) {
        String nombre = (ni.getName() + " " + ni.getDisplayName()).toLowerCase(Locale.ROOT);
        for (String v : new String[]{"vethernet", "docker", "wsl", "hyper-v", "virtualbox", "vmware", "vbox", "virtual", "bluetooth"}) {
            if (nombre.contains(v)) {
                return true;
            }
        }
        return false;
    }
}
