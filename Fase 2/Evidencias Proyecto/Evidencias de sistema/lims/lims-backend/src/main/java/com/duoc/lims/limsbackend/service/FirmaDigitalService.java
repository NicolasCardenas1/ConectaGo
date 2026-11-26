package com.duoc.lims.limsbackend.service;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfDate;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSignature;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * v7 — Firma digital de los informes PDF.
 *
 * El PDF se firma con el certificado del laboratorio (firma CMS/PKCS#7 desprendida, SHA-256 con RSA,
 * formato adbe.pkcs7.detached que reconocen Adobe Reader y otros lectores). Cualquier cambio en el
 * archivo después de firmarlo invalida la firma.
 *
 * El certificado se guarda en un almacén PKCS#12 ({@code lims.firma.almacen}, por defecto
 * {@code firma/laboratorio.p12}, fuera de git). Si no existe, se crea uno AUTOFIRMADO al primer uso:
 * sirve para demostrar integridad, pero no tiene validez legal. Para una firma electrónica avanzada
 * se reemplaza el archivo por el certificado emitido por un prestador acreditado y se configura su clave.
 */
@Service
public class FirmaDigitalService {

    private static final Logger log = LoggerFactory.getLogger(FirmaDigitalService.class);
    private static final String ALIAS = "laboratorio";
    /** Espacio reservado en el PDF para la firma (bytes); sobra para un certificado RSA 2048. */
    private static final int ESPACIO_FIRMA = 8192;
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // sin 0/O ni 1/I
    private static final SecureRandom AZAR = new SecureRandom();

    private final Path almacen;
    private final char[] clave;
    private final String nombreLaboratorio;

    private PrivateKey llave;
    private Certificate[] cadena;

    public FirmaDigitalService(@Value("${lims.firma.almacen:firma/laboratorio.p12}") String almacen,
                               @Value("${lims.firma.clave:conectago-demo}") String clave,
                               @Value("${lims.firma.laboratorio:Laboratorio ConectaGo LIMS}") String nombreLaboratorio) {
        this.almacen = Paths.get(almacen);
        this.clave = clave.toCharArray();
        this.nombreLaboratorio = nombreLaboratorio;
    }

    /** Datos de quién firma, para el diccionario de firma del PDF. */
    public record Firmante(String nombre, String rol, String razon) {}

    /** Firma el PDF y devuelve el archivo firmado. */
    public byte[] firmar(byte[] pdf, Firmante firmante, Calendar fecha) {
        try {
            cargarCertificado();
            PdfReader lector = new PdfReader(pdf);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfStamper stamper = PdfStamper.createSignature(lector, salida, '\0');
            PdfSignatureAppearance apariencia = stamper.getSignatureAppearance();
            apariencia.setSignDate(fecha);
            apariencia.setReason(firmante.razon());
            apariencia.setLocation(nombreLaboratorio);

            PdfSignature diccionario = new PdfSignature(PdfName.ADOBE_PPKLITE, PdfName.ADBE_PKCS7_DETACHED);
            diccionario.setReason(firmante.razon());
            diccionario.setLocation(nombreLaboratorio);
            diccionario.setName(firmante.nombre() + " (" + firmante.rol() + ")");
            diccionario.setDate(new PdfDate(fecha));
            apariencia.setCryptoDictionary(diccionario);
            apariencia.preClose(Map.of(PdfName.CONTENTS, ESPACIO_FIRMA * 2 + 2));

            byte[] firma = firmaCms(leerTodo(apariencia.getRangeStream()));
            if (firma.length > ESPACIO_FIRMA) {
                throw new IllegalStateException("La firma no cabe en el espacio reservado del PDF");
            }
            byte[] relleno = new byte[ESPACIO_FIRMA];
            System.arraycopy(firma, 0, relleno, 0, firma.length);
            PdfDictionary contenido = new PdfDictionary();
            contenido.put(PdfName.CONTENTS, new PdfString(relleno).setHexWriting(true));
            apariencia.close(contenido);
            return salida.toByteArray();
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("No se pudo firmar el informe PDF: " + e.getMessage(), e);
        }
    }

    /** Código corto para verificar el informe en el LIMS (ej. 7F3A-9C2K). */
    public String nuevoCodigoVerificacion() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Huella SHA-256 (hexadecimal) del archivo, para comprobar que no fue modificado. */
    public static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Titular del certificado, para mostrarlo en el informe. */
    public String titularCertificado() {
        cargarCertificado();
        return ((X509Certificate) cadena[0]).getSubjectX500Principal().getName();
    }

    // ---------- certificado ----------

    private synchronized void cargarCertificado() {
        if (llave != null) {
            return;
        }
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            if (Files.exists(almacen)) {
                try (InputStream in = Files.newInputStream(almacen)) {
                    ks.load(in, clave);
                }
            } else {
                ks.load(null, null);
                crearAutofirmado(ks);
                Path carpeta = almacen.toAbsolutePath().getParent();
                if (carpeta != null) {
                    Files.createDirectories(carpeta);
                }
                try (OutputStream out = Files.newOutputStream(almacen)) {
                    ks.store(out, clave);
                }
                log.warn("Firma digital: se creó un certificado AUTOFIRMADO en {} (válido para demostración, "
                        + "sin validez legal)", almacen.toAbsolutePath());
            }
            String alias = ks.containsAlias(ALIAS) ? ALIAS : ks.aliases().nextElement();
            llave = (PrivateKey) ks.getKey(alias, clave);
            cadena = ks.getCertificateChain(alias);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el certificado de firma (" + almacen + "): "
                    + e.getMessage(), e);
        }
    }

    private void crearAutofirmado(KeyStore ks) throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048, AZAR);
        KeyPair par = generador.generateKeyPair();
        X500Name titular = new X500Name("CN=" + nombreLaboratorio + " (certificado de demostración), O="
                + nombreLaboratorio + ", C=CL");
        Instant ahora = Instant.now();
        X509v3CertificateBuilder constructor = new JcaX509v3CertificateBuilder(titular,
                new BigInteger(64, AZAR), Date.from(ahora.minus(1, ChronoUnit.DAYS)),
                Date.from(ahora.plus(5 * 365, ChronoUnit.DAYS)), titular, par.getPublic());
        ContentSigner firmador = new JcaContentSignerBuilder("SHA256withRSA").build(par.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(constructor.build(firmador));
        ks.setKeyEntry(ALIAS, par.getPrivate(), clave, new Certificate[]{cert});
    }

    private byte[] firmaCms(byte[] datos) {
        try {
            X509Certificate cert = (X509Certificate) cadena[0];
            CMSSignedDataGenerator generador = new CMSSignedDataGenerator();
            ContentSigner firmador = new JcaContentSignerBuilder("SHA256withRSA").build(llave);
            generador.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().build()).build(firmador, cert));
            generador.addCertificates(new JcaCertStore(List.of(cadena)));
            // DER (largo definido): es lo que espera la norma de PDF; BouncyCastle por defecto usa BER.
            return generador.generate(new CMSProcessableByteArray(datos), false).getEncoded("DER");
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la firma: " + e.getMessage(), e);
        }
    }

    private static byte[] leerTodo(InputStream in) throws IOException {
        try (in) {
            return in.readAllBytes();
        }
    }
}
