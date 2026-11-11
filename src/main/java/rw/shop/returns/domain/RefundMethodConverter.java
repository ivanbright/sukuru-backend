package rw.shop.returns.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RefundMethodConverter implements AttributeConverter<RefundMethod, String> {

    @Override
    public String convertToDatabaseColumn(RefundMethod attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public RefundMethod convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RefundMethod.valueOf(dbData.toUpperCase());
    }
}
