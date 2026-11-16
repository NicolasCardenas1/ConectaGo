package com.duoc.lims.limsbackend.model.converter;

import com.duoc.lims.limsbackend.model.enums.TipoCentro;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TipoCentroConverter implements AttributeConverter<TipoCentro, String> {

    @Override
    public String convertToDatabaseColumn(TipoCentro attribute) {
        return attribute == null ? null : attribute.getValorDb();
    }

    @Override
    public TipoCentro convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TipoCentro.fromValorDb(dbData);
    }
}