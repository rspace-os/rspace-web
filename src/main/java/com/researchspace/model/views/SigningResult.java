package com.researchspace.model.views;

import java.util.Optional;

import com.researchspace.model.Signature;
import com.researchspace.model.record.Record;
/**
 * POJO class to hold result of an attempt to sign a record
 */
public class SigningResult {
	public static final String DOC_SIGN_SUCCESS_MSG = "This document has been signed and locked successfully.";

	private Record signed;
	private Signature signature;
	
	/**
	 * May be <code>null</code> if signing failed.
	 * @return
	 */
	public Optional<Signature> getSignature() {
		return Optional.ofNullable(signature);
	}

	public Record getSigned() {
		return signed;
	}

	public String getMsg() {
		return msg;
	}

	private String msg;

	/**
	 * 
	 * @param signed
	 * @param msg
	 * @param signature can be <code>null</code> if signing failed.
	 */
	public SigningResult(Record signed, String msg, Signature signature) {
		super();
		this.signed = signed;
		this.msg = msg;
		this.signature = signature;
	}
	
	/**
	 * Convenience method to determine if signing was successful
	 * @return
	 */
	public boolean isSuccessful() {
		return DOC_SIGN_SUCCESS_MSG.equals(msg);
	}

}
