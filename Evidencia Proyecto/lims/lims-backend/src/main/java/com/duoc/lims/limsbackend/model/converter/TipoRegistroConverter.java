package com.duoc.lims.limsbackend.model.converter;

import com.duoc.lims.limsbackend.model.enums.TipoRegistro;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TipoRegistroConverter implements AttributeConverter<TipoRegistro, String> {

    @Override
    public String convertToDatabaseColumn(TipoRegistro attribute) {
        return attribute == null ? null : attribute.getValorDb();
    }

    @Override
    public TipoRegistro convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TipoRegistro.fromValorDb(dbData);
    }
}