package com.researchspace.model.units;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Data;
@Data
public class CurieToBeqInput {
	
	@NotNull
	@Min(0)
	private Double value;
	
	@Pattern(regexp="(Ci)|(mCi)", message="Possible units are Ci or mCi")
	private String unit;

}
