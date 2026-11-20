package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolicitudResetPasswordDTO {

    @NotBlank(message = "username es obligatorio")
    private String username;
}