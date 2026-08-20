package com.researchspace.model.preference;

import com.researchspace.model.record.StructuredDocument;

public enum ExportPageSize {
	
	/**
	 * DEFAULT
	 */
	A4(595),
	
	/**
	 * US_LETTER
	 */
	LETTER(612),
	
	/**
	 * Default if not set
	 */
	UNKNOWN(-1);
	
	private int pdfDocWidth;

	ExportPageSize(int size) {
		this.pdfDocWidth = size;
	}

	public int getPdfDocWidth() {
		return pdfDocWidth;
	}
	
	public float scaleFactor() {
		return (float) getPdfDocWidth() / (float) StructuredDocument.DEFAULT_WIDTH;
	}
}