package com.duoc.lims.limsbackend.model.converter;

import com.duoc.lims.limsbackend.model.enums.Prioridad;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PrioridadConverter implements AttributeConverter<Prioridad, String> {

    @Override
    public String convertToDatabaseColumn(Prioridad attribute) {
        return attribute == null ? null : attribute.getValorDb();
    }

    @Override
    public Prioridad convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Prioridad.fromValorDb(dbData);
    }
}