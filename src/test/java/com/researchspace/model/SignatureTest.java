package com.researchspace.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.testutil.JakartaValidatorTest;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class SignatureTest extends JakartaValidatorTest {

  @Test
  public void nonStructuredRecordSigningResolvesValidationMessage() {
    Signature signature = new Signature();
    signature.setRecordSigned(new EcatImage());

    UnsupportedOperationException exception =
        assertThrows(UnsupportedOperationException.class, signature::generateRecordContentHash);

    assertEquals(
        "only structured documents can be signed",
        new MessageSourceUtils(new JsonMessageSource()).getExceptionMessage(exception));
    assertThat(signature.getHashes()).isEmpty();
  }

  @Test
  public void testSetUp() {
    Signature sig = new Signature();
    User u = TestFactory.createAnyUser("any");
    User witnessU = TestFactory.createAnyUser("witness");
    Witness witness = new Witness(witnessU);

    StructuredDocument r = (StructuredDocument) TestFactory.createAnyRecord(u);

    sig.setRecordSigned(r);
    sig.setSigner(u);
    sig.addWitnesses(Arrays.asList(new Witness[] {witness}));
    assertThat(sig.getWitnesses()).hasSize(1);
    assertEquals(sig, witness.getSignature());

    sig.generateRecordContentHash();
    assertThat(sig.getHashes()).hasSize(1);
    assertEquals(
        r.getRecordContentHashForSigning().toHex(),
        ((SignatureHash) sig.getHashes().toArray()[0]).getHexValue());
  }

  @Test
  public void testSignatureHash() {
    SignatureHash hash = new SignatureHash();
    hash.setHexValue("abc");
    assertNErrors(hash, 1, true);

    hash.setHexValue(SecureStringUtils.getHashForSigning("something").toHex());
    assertNErrors(hash, 0);
  }
}
