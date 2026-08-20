package com.researchspace.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of="id")
public class SignatureHashInfo {

    private Long id;
    private String hexValue;
    private String type;
    private Long filePropertyId;
    
}
