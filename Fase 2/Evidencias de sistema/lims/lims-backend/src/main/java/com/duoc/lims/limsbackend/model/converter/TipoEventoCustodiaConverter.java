package com.duoc.lims.limsbackend.model.converter;

import com.duoc.lims.limsbackend.model.enums.TipoEventoCustodia;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TipoEventoCustodiaConverter implements AttributeConverter<TipoEventoCustodia, String> {

    @Override
    public String convertToDatabaseColumn(TipoEventoCustodia attribute) {
        return attribute == null ? null : attribute.getValorDb();
    }

    @Override
    public TipoEventoCustodia convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TipoEventoCustodia.fromValorDb(dbData);
    }
}
