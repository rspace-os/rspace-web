package com.researchspace.model.permissions;
/**
 * Interface for symmetric text encryption functionality
 */
public interface TextEncryptor {
	
	/**
	 * Noop encryptor that does not perform any encryption/decryption ops.
	 */
	public static final TextEncryptor NOOP = new TextEncryptor () {

		@Override
		public String encrypt(String text) {
		   return text;
		}

		@Override
		public String decrypt(String encryptedText) {
			return encryptedText;
		}
		
	};

	/**
	 * Encrypts and returns encrypted text as base64 string
	 * 
	 * @param text
	 * @return
	 */
	String encrypt(String text);

	/**
	 * 
	 * @param encryptedText
	 *            in Base64 format
	 * @return
	 */
	String decrypt(String encryptedText);

}