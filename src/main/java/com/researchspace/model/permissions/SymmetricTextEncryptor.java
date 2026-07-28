package com.researchspace.model.permissions;

import java.io.Serializable;
import java.security.Key;

import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.lang.codec.Base64;
import org.apache.shiro.lang.codec.CodecSupport;
import org.apache.shiro.crypto.cipher.AesCipherService;
import org.apache.shiro.crypto.cipher.OperationMode;
import org.apache.shiro.crypto.cipher.PaddingScheme;
import org.apache.shiro.crypto.hash.Sha384Hash;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.crypto.cipher.ByteSourceBroker;

/**
 * Convenience class to encrypt  small pieces of text using a symmetric key, which can be
 * supplied e.g via a deployment property or external source.<br>
 * 
 * Uses ECB mode  - repeated encryption of the same string produces the same result.
 * 
 * This is not suitable for encryption of large documents, where default CBC mode is better.
 */
public class SymmetricTextEncryptor implements TextEncryptor, Serializable {

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private byte[] keyBytes;
	// this does not implement serializable and does not store state.
	private transient AesCipherService service ; 

	 SymmetricTextEncryptor() {
		 super();
		 service = new AesCipherService();
		 service.setMode(OperationMode.ECB);
		 // see shiro 1.5.1 release notes for why this is needed
		 service.setPaddingScheme(PaddingScheme.PKCS5);
	 }
	 
	public SymmetricTextEncryptor(byte[] keyBytes) {
		this();
		assertArgs(keyBytes);
		this.keyBytes = keyBytes;
	}
	
	/**
	 * Must be a Base64 encoded key that is suitable for AES algorithm.
	 * 
	 * @param base64EncodedKeyString
	 */
	public SymmetricTextEncryptor(String base64EncodedKeyString) {
		this();
		byte[] bytes = Base64.decode(base64EncodedKeyString);
		assertArgs(bytes);
		Key key = new SecretKeySpec(bytes, 0, bytes.length, "AES");
		this.keyBytes = key.getEncoded();
	}

	private void assertArgs(byte[] keyBytes) {
		Validate.isTrue(keyBytes!=null && isValidAESKeyLength(keyBytes),  " must be valid AES key length but was " 
		   + keyBytes==null?"null":keyBytes.length+"");
	}

	private boolean isValidAESKeyLength(byte[] keyBytes) {
		return keyBytes.length == 32 || keyBytes.length ==24 ||keyBytes.length==16;
	}

	
	
	/**
	 * Static factory to generates a SymmetricTextEncryptor with AES-128 bit key derived from a SHA-384 digest
	 *  of the input source string.
	 * @param keySource between 1 and 255 chars.
	 * @return
	 */
	public static SymmetricTextEncryptor  createFromAnyString(String keySource) {
		Validate.isTrue(!StringUtils.isEmpty(keySource), "input string cannot be empty");
		Validate.isTrue(keySource.length() < 255, "input string must be < 255 chars");
		SimpleHash hash = new SimpleHash(Sha384Hash.ALGORITHM_NAME, keySource);
		byte [] byte32 = hash.getBytes();
		byte[] byte16 =  new byte [16];
		System.arraycopy(byte32, 0, byte16,0,16);
		return new SymmetricTextEncryptor(byte16);	
	}

	/* (non-Javadoc)
	 * @see com.researchspace.model.permissions.TextEncryptor#encrypt(java.lang.String)
	 */
	@Override
	public String encrypt(String text) {
		return service.encrypt(CodecSupport.toBytes(text), keyBytes).toBase64();
	}

	/* (non-Javadoc)
	 * @see com.researchspace.model.permissions.TextEncryptor#decrypt(java.lang.String)
	 */
	@Override
	public String decrypt(String encryptedText) {
		ByteSourceBroker decrypted =
				service.decrypt(Base64.decode(CodecSupport.toBytes(encryptedText)), keyBytes);
		return CodecSupport.toString(decrypted.getClonedBytes());
	}

}
