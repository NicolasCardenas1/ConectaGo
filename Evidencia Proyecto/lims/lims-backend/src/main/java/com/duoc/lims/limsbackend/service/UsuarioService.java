package com.duoc.lims.limsbackend.service;

import com.duoc.lims.limsbackend.dto.LoginRequestDTO;
import com.duoc.lims.limsbackend.dto.UsuarioRequestDTO;
import com.duoc.lims.limsbackend.dto.UsuarioResponseDTO;
import com.duoc.lims.limsbackend.model.Centro;
import com.duoc.lims.limsbackend.model.Rol;
import com.duoc.lims.limsbackend.model.Usuario;
import com.duoc.lims.limsbackend.repository.CentroRepository;
import com.duoc.lims.limsbackend.repository.RolRepository;
import com.duoc.lims.limsbackend.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final CentroRepository centroRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UsuarioService(UsuarioRepository usuarioRepository,
                           RolRepository rolRepository,
                           CentroRepository centroRepository) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.centroRepository = centroRepository;
    }

    public List<UsuarioResponseDTO> listar() {
        return usuarioRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public UsuarioResponseDTO crear(UsuarioRequestDTO dto) {
        Centro centro = centroRepository.findById(dto.getIdCentro())
                .orElseThrow(() -> new IllegalArgumentException("Centro no encontrado: " + dto.getIdCentro()));

        Rol rol = rolRepository.findById(dto.getIdRol())
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + dto.getIdRol()));

        if (usuarioRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new IllegalArgumentException("El username ya existe: " + dto.getUsername());
        }

        Usuario usuario = new Usuario();
        usuario.setCentro(centro);
        usuario.setNombre(dto.getNombre());
        usuario.setApellido(dto.getApellido());
        usuario.setEmail(dto.getEmail());
        usuario.setUsername(dto.getUsername());
        usuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        usuario.setRol(rol);
        usuario.setActivo(true);

        Usuario guardado = usuarioRepository.save(usuario);
        return toResponseDTO(guardado);
    }

    public UsuarioResponseDTO login(LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Usuario o contraseña incorrectos"));

        if (!usuario.isActivo()) {
            throw new IllegalArgumentException("El usuario esta inactivo");
        }

        if (!passwordEncoder.matches(dto.getPassword(), usuario.getPasswordHash())) {
            throw new IllegalArgumentException("Usuario o contraseña incorrectos");
        }

        return toResponseDTO(usuario);
    }

    public void solicitarResetPassword(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("No existe un usuario con ese username"));
        usuario.setRequiereResetPassword(true);
        usuarioRepository.save(usuario);
    }

    public List<UsuarioResponseDTO> listarSolicitudesReset() {
        return usuarioRepository.findByRequiereResetPasswordTrue().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public UsuarioResponseDTO resetearPassword(Integer id, String nuevaPassword) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuario.setRequiereResetPassword(false);
        Usuario actualizado = usuarioRepository.save(usuario);
        return toResponseDTO(actualizado);
    }

    private UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmail(),
                usuario.getUsername(),
                usuario.getRol().getNombreRol(),
                usuario.getCentro().getNombreCentro(),
                usuario.isActivo(),
                usuario.isRequiereResetPassword()
        );
    }
}