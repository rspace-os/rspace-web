package com.researchspace.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SignatureInfoTest {
	
	@Test
	public void testToJSONConversion() {
		SignatureInfo signatureInfo = new SignatureInfo();
		signatureInfo.setId(1L);
		signatureInfo.setSignerFullName("signer");
		signatureInfo.setSignDate("03/11/16 Thu 15:20 GMT");
		signatureInfo.getWitnesses().put("wit1", null);
		signatureInfo.setStatus(SignatureStatus.AWAITING_WITNESS);

		String asJSON = signatureInfo.getAsJSON();
		assertEquals("{\"id\":1,\"signerFullName\":\"signer\",\"signDate\":\"03/11/16 Thu 15:20 GMT\",\"witnesses\":{\"wit1\":null},\"status\":\"AWAITING_WITNESS\",\"hashes\":[]}", asJSON);
	}

}
