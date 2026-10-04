package com.duoc.lims.limsbackend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.lims.limsbackend.dto.LoginRequestDTO;
import com.duoc.lims.limsbackend.dto.ResetPasswordDTO;
import com.duoc.lims.limsbackend.dto.SolicitudResetPasswordDTO;
import com.duoc.lims.limsbackend.dto.UsuarioRequestDTO;
import com.duoc.lims.limsbackend.dto.UsuarioResponseDTO;
import com.duoc.lims.limsbackend.service.UsuarioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioResponseDTO> listar() {
        return usuarioService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponseDTO crear(@Valid @RequestBody UsuarioRequestDTO dto) {
        return usuarioService.crear(dto);
    }

    @PostMapping("/login")
    public UsuarioResponseDTO login(@Valid @RequestBody LoginRequestDTO dto) {
        return usuarioService.login(dto);
    }

    @PostMapping("/solicitar-reset")
    public String solicitarReset(@Valid @RequestBody SolicitudResetPasswordDTO dto) {
        usuarioService.solicitarResetPassword(dto.getUsername());
        return "Solicitud registrada. Un administrador se pondra en contacto contigo.";
    }

    @GetMapping("/solicitudes-reset")
    public List<UsuarioResponseDTO> solicitudesReset() {
        return usuarioService.listarSolicitudesReset();
    }

    @PutMapping("/{id}/resetear-password")
    public UsuarioResponseDTO resetearPassword(@PathVariable Integer id, @Valid @RequestBody ResetPasswordDTO dto) {
        return usuarioService.resetearPassword(id, dto.getNuevaPassword());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarArgumentoInvalido(IllegalArgumentException ex) {
        return ex.getMessage();
    }
}