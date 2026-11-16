package com.duoc.lims.limsbackend.model.converter;

import com.duoc.lims.limsbackend.model.enums.EstadoMuestra;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EstadoMuestraConverter implements AttributeConverter<EstadoMuestra, String> {

    @Override
    public String convertToDatabaseColumn(EstadoMuestra attribute) {
        return attribute == null ? null : attribute.getValorDb();
    }

    @Override
    public EstadoMuestra convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EstadoMuestra.fromValorDb(dbData);
    }
}