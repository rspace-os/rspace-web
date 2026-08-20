package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class URIFieldFormTest {

	@ParameterizedTest
	@MethodSource()
	@DisplayName("Valid URIs are accepted")
	void validate(String validURI) {
		URIFieldForm rff = new URIFieldForm("uriField");
		assertFalse(rff.validate(validURI).hasErrorMessages());
	}
	
	@ParameterizedTest
	@MethodSource()
	@DisplayName("InValid URIs not accepted")
	void notValid(String validURI) {
		URIFieldForm rff = new URIFieldForm("uriField");
		assertFalse(rff.validate(validURI).hasErrorMessages());
	}
	
	static List<String> validate (){
		return Arrays.asList("file.txt", "https://pubchem.ncbi.nlm.nih.gov/compound/180", "urn:a.valid.urn", "file:/etc/home/mydata.txt");
	}
	
	static List<String> notValid (){
		return Arrays.asList("https??", "???", "12345");
	}
}
