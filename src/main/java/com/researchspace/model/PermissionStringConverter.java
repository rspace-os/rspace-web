package com.researchspace.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Converter
public class PermissionStringConverter implements AttributeConverter<Set, String> {
    @Override
    public String convertToDatabaseColumn(Set permissionList) {
        if(permissionList == null){
            return null;
        }
        return String.join(";;", permissionList);
    }

    @Override
    public Set<String> convertToEntityAttribute(String permissionString) {
        if(permissionString == null || permissionString.isEmpty()){
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(permissionString.split(";;")));
    }
}
