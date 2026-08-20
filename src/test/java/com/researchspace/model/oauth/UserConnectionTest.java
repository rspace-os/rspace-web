package com.researchspace.model.oauth;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.researchspace.model.permissions.SymmetricTextEncryptor;
import com.researchspace.model.record.TestFactory;


public class UserConnectionTest {

	@Test
	@DisplayName("equality based on embedded ID")
	void testEqualsHashcode() {
		
		UserConnectionId userConnectionId = new UserConnectionId("user", "provider", "userProviderId");	
		UserConnection userConnection = new UserConnection(userConnectionId, "Token");
		
		UserConnection userConnection2 = new UserConnection(userConnectionId,"Token");
		assertEquals(userConnection, userConnection2);
		assertEquals(userConnection.hashCode(), userConnection2.hashCode());
	}
	
	@Test
	public void encryptDecryptInvariants () {
		SymmetricTextEncryptor enc = setUpEncryptor();
		UserConnection someConnection = TestFactory.createUserConnection("anyuser");
		String originalAccess = someConnection.getAccessToken();
		String originalRefresh =  someConnection.getRefreshToken();
		String originalSecret =  someConnection.getSecret();
		final String expectedEncryptedAccess = enc.encrypt(someConnection.getAccessToken());
		final String expectedEncryptedRefresh = enc.encrypt(someConnection.getRefreshToken());
		final String expectedEncryptedSecret = enc.encrypt(someConnection.getSecret());
	
		someConnection.encryptTokens(enc);
		assertEncryption(someConnection, expectedEncryptedAccess, expectedEncryptedRefresh, expectedEncryptedSecret);
		//encrypting twice doesn't happen
		someConnection.encryptTokens(enc);
		assertEncryption(someConnection, expectedEncryptedAccess, expectedEncryptedRefresh, expectedEncryptedSecret);
	 
		someConnection.decryptTokens(enc);
		assertEquals (originalAccess, someConnection.getAccessToken() );
		assertEquals (originalRefresh, someConnection.getRefreshToken() );
		assertEquals (originalSecret, someConnection.getSecret() );
		
		//decrypt twice - no effect.
		someConnection.decryptTokens(enc);
		assertEquals (originalAccess, someConnection.getAccessToken() );
		assertEquals (originalRefresh, someConnection.getRefreshToken() );
		assertEquals (originalSecret, someConnection.getSecret() );
	
	}

	private SymmetricTextEncryptor setUpEncryptor() {
		byte [] key  =  RandomUtils.nextBytes(16);
		return new SymmetricTextEncryptor(key);
	}
	
	@Test
	public void noDecryptionIfIsEncryptedFalse () {
		SymmetricTextEncryptor enc = setUpEncryptor();
		UserConnection someConnection = TestFactory.createUserConnection("anyuser");
		assertFalse(someConnection.isTransientlyEncrypted());
		assertFalse(someConnection.isEncrypted());
		String originalAccess = someConnection.getAccessToken();
		someConnection.decryptTokens(enc); // has no effect
		assertEquals (originalAccess, someConnection.getAccessToken() );
		
	}

	private void assertEncryption(UserConnection someConnection, final String expectedEncryptedAccess,
			final String expectedEncryptedRefresh, final String expectedEncryptedSecret) {
		assertEquals (expectedEncryptedAccess, someConnection.getAccessToken() );
		assertEquals (expectedEncryptedRefresh, someConnection.getRefreshToken() );
		assertEquals (expectedEncryptedSecret, someConnection.getSecret() );
	}

}
