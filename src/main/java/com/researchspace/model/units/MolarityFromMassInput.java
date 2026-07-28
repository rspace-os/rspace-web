package com.researchspace.model.units;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;
@Data
public class MolarityFromMassInput implements VolumeInput, MassInput, RfmInput {
	
	@NotNull
	@Min(value = 0, message = "{valid.rfm.msg}")
	private Double rfm;

	@NotNull
	@Min(value = 0)
	@Max(value = 1000_000_000)
	private Double massValue;

	@NotNull
	private String massUnit;

	@NotNull
	@Min(value = 0)
	@Max(value = 1000_000_000)
	private Double volValue;

	@NotNull
	private String volUnit;

}
