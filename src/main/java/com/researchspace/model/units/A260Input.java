package com.researchspace.model.units;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@ValidA260Input
@AllArgsConstructor
@NoArgsConstructor
public class A260Input {
	@NotNull
	@DecimalMin(value="0", inclusive=false)
	@Max(100)
	private Double a260;
	
	@Min(0)
	@Max(100)
	private Double a280 ;
	
	@Min(0)
	@Max(100)
	private Double a320;
	
	@Pattern(regexp="(50)|(40)|(35)|(20)")
	private String natype;


}
