package com.duoc.lims.limsbackend.model.converter;

import com.duoc.lims.limsbackend.model.enums.TipoMuestra;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TipoMuestraConverter implements AttributeConverter<TipoMuestra, String> {

    @Override
    public String convertToDatabaseColumn(TipoMuestra attribute) {
        return attribute == null ? null : attribute.getValorDb();
    }

    @Override
    public TipoMuestra convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TipoMuestra.fromValorDb(dbData);
    }
}