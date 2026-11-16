package com.duoc.lims.limsbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordDTO {

    @NotBlank(message = "nuevaPassword es obligatoria")
    private String nuevaPassword;
}