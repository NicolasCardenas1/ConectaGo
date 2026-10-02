package com.duoc.lims.limsbackend.model.converter;

import com.duoc.lims.limsbackend.model.enums.EstadoMuestraAnalisis;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EstadoMuestraAnalisisConverter
        implements AttributeConverter<EstadoMuestraAnalisis, String> {

    @Override
    public String convertToDatabaseColumn(EstadoMuestraAnalisis estado) {
        return estado == null ? null : estado.getValorDb();
    }

    @Override
    public EstadoMuestraAnalisis convertToEntityAttribute(String valor) {
        return valor == null ? null : EstadoMuestraAnalisis.fromValorDb(valor);
    }
}