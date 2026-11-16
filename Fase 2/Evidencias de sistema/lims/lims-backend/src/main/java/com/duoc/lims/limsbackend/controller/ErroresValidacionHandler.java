package com.duoc.lims.limsbackend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * v5 — Errores de validación (@Valid) para todos los controladores: devuelve un 400 con el mensaje
 * legible (ej. "el motivo del cambio es obligatorio") en vez del JSON técnico de Spring con la traza.
 * Es el mismo formato de texto plano que ya usan los @ExceptionHandler de cada controlador.
 */
@RestControllerAdvice
public class ErroresValidacionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarValidacion(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
    }
}
