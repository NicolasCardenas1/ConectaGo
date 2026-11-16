package com.duoc.lims.limsbackend.controller;

import com.duoc.lims.limsbackend.dto.LoginRequestDTO;
import com.duoc.lims.limsbackend.dto.LoginResponseDTO;
import com.duoc.lims.limsbackend.dto.ResetPasswordDTO;
import com.duoc.lims.limsbackend.dto.SolicitudResetPasswordDTO;
import com.duoc.lims.limsbackend.dto.UsuarioRequestDTO;
import com.duoc.lims.limsbackend.dto.UsuarioResponseDTO;
import com.duoc.lims.limsbackend.security.SesionService;
import com.duoc.lims.limsbackend.security.UsuarioAutenticado;
import com.duoc.lims.limsbackend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Usuarios y autenticación (RF06).
 * Públicos: login, registro (siempre como Analista) y solicitar-reset.
 * Con sesión: logout. Solo Administrador: el resto (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final SesionService sesionService;

    public UsuarioController(UsuarioService usuarioService, SesionService sesionService) {
        this.usuarioService = usuarioService;
        this.sesionService = sesionService;
    }

    // ---------- públicos ----------

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO dto) {
        return usuarioService.login(dto);
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponseDTO registro(@Valid @RequestBody UsuarioRequestDTO dto) {
        return usuarioService.registrarPublico(dto);
    }

    @PostMapping("/solicitar-reset")
    public String solicitarReset(@Valid @RequestBody SolicitudResetPasswordDTO dto) {
        usuarioService.solicitarResetPassword(dto.getUsername());
        return "Solicitud registrada. Un administrador se pondra en contacto contigo.";
    }

    // ---------- con sesión ----------

    @PostMapping("/logout")
    public String logout(@RequestHeader(value = "Authorization", required = false) String autorizacion) {
        if (autorizacion != null && autorizacion.startsWith("Bearer ")) {
            sesionService.cerrar(autorizacion.substring("Bearer ".length()).trim());
        }
        return "Sesión cerrada";
    }

    // ---------- solo Administrador ----------

    @GetMapping
    public List<UsuarioResponseDTO> listar() {
        return usuarioService.listar();
    }

    /** El Administrador crea usuarios con cualquier rol (Analista, Supervisor o Administrador). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponseDTO crear(@Valid @RequestBody UsuarioRequestDTO dto) {
        return usuarioService.crear(dto);
    }

    @GetMapping("/solicitudes-reset")
    public List<UsuarioResponseDTO> solicitudesReset() {
        return usuarioService.listarSolicitudesReset();
    }

    @PutMapping("/{id}/resetear-password")
    public UsuarioResponseDTO resetearPassword(@PathVariable Integer id, @Valid @RequestBody ResetPasswordDTO dto) {
        return usuarioService.resetearPassword(id, dto.getNuevaPassword());
    }

    @PatchMapping("/{id}/estado")
    public UsuarioResponseDTO cambiarEstado(@PathVariable Integer id,
                                            @RequestParam boolean activo,
                                            @AuthenticationPrincipal UsuarioAutenticado yo) {
        return usuarioService.cambiarEstado(id, activo, yo.id());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}
