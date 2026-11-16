package com.duoc.lims.limsbackend.model.converter;

import com.duoc.lims.limsbackend.model.enums.EstadoAprobacion;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EstadoAprobacionConverter
        implements AttributeConverter<EstadoAprobacion, String> {

    @Override
    public String convertToDatabaseColumn(EstadoAprobacion estado) {
        return estado == null ? null : estado.getValorDb();
    }

    @Override
    public EstadoAprobacion convertToEntityAttribute(String valor) {
        return valor == null ? null : EstadoAprobacion.fromValorDb(valor);
    }
}