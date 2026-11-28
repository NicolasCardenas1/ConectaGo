package com.duoc.lims.limsbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * v9 — Lo único que se puede abrir desde internet es la página del QR.
 *
 * Con el túnel público (iniciar-tunel-qr.ps1) cualquier persona puede llegar al backend. Las solicitudes
 * que pasan por un túnel o proxy traen encabezados como {@code Cf-Connecting-Ip} o {@code X-Forwarded-For};
 * a esas solo se les permite {@code GET /m/{codigo}} (la consulta firmada de la etiqueta). La API, el login
 * y todo lo demás responden 404, así nadie puede probar contraseñas desde afuera. El cliente JavaFX se
 * conecta directo a localhost y no se ve afectado.
 */
public class AccesoExternoFilter extends OncePerRequestFilter {

    private static final String[] ENCABEZADOS_PROXY = {"Cf-Connecting-Ip", "Cf-Ray", "X-Forwarded-For", "Forwarded"};

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (viaTunel(req) && !esPaginaQr(req)) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().write("No encontrado.");
            return;
        }
        chain.doFilter(req, res);
    }

    static boolean viaTunel(HttpServletRequest req) {
        for (String h : ENCABEZADOS_PROXY) {
            if (req.getHeader(h) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean esPaginaQr(HttpServletRequest req) {
        String ruta = req.getRequestURI();
        return "GET".equals(req.getMethod()) && ruta.startsWith("/m/") && ruta.indexOf('/', 3) < 0;
    }
}
