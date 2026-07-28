package com.researchspace.model.units;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BacterialTransformationInput {

	@DecimalMin(inclusive = false, value = "0")
	private Double dnaConc;
	@DecimalMin(inclusive = false, value = "0")
	private Double dnaVol;
	@DecimalMin(inclusive = false, value = "0")
	private Double transformationTotalVol;
	@DecimalMin(inclusive = false, value = "0")
	private Double platedVol;
	@Min(0)
	private Integer numColonies;

}
