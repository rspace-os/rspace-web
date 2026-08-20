package com.researchspace.model;
/**
 * The format that the 'chemElements' column is stored in.
 */
public enum ChemElementsFormat {
	MOL("mol"),
	MRV("mrv"),
	KET("ket"),
	CDXML("cdxml"),
	SMI("smi");
	
	public String getLabel() {
		return label;
	}

	private String label;

	private ChemElementsFormat(String label) {
		this.label = label;
	}
	

}
