package com.researchspace.model;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import com.researchspace.core.testutil.JakartaValidatorTest;
import org.junit.Test;

import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;

public class SignatureTest extends JakartaValidatorTest {

	@Test
	public void testSetUp() {
		Signature sig = new Signature();
		User u = TestFactory.createAnyUser("any");
		User witnessU = TestFactory.createAnyUser("witness");
		Witness witness = new Witness(witnessU);
		
		StructuredDocument r = (StructuredDocument) TestFactory.createAnyRecord(u);
		
        sig.setRecordSigned(r);
        sig.setSigner(u);
        sig.addWitnesses(Arrays.asList(new Witness[] { witness }));
        assertEquals(1, sig.getWitnesses().size());
        assertEquals(sig, witness.getSignature());

        sig.generateRecordContentHash();
		assertEquals(1, sig.getHashes().size());
		assertEquals(r.getRecordContentHashForSigning().toHex(), 
		        ((SignatureHash) sig.getHashes().toArray()[0]).getHexValue());
	}

	@Test
	public void testSignatureHash() {
		SignatureHash hash = new SignatureHash();
		hash.setHexValue("abc");
		assertNErrors(hash, 1,true);
		
		hash.setHexValue(SecureStringUtils.getHashForSigning("something").toHex());
		assertNErrors(hash, 0);
	}
}
