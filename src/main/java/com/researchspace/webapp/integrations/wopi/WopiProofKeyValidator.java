package com.researchspace.webapp.integrations.wopi;

import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiPublicKeys;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import javax.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** The class verifying that requests originate from Office Online by using proof keys. */
@Slf4j
public class WopiProofKeyValidator {

  private boolean timestampVerificationEnabled = true;

  private WopiPublicKeys wopiPublicKeys;

  public void setPublicKeys(WopiPublicKeys publicKeys) {
    this.wopiPublicKeys = publicKeys;
  }

  /**
   * Checks that proofHeader (or oldProofHeader) is a valid signature for the given request. Also
   * verifies the timestamp is within last 20-minute period.
   *
   * @return true if proof is valid for the request
   */
  public boolean verifyWopiRequest(
      String accessToken,
      String requestUrl,
      String proofHeader,
      String oldProofHeader,
      String timestampHeader) {

    if (wopiPublicKeys == null) {
      log.warn("requested proof key validation, but WOPI public keys not available");
      return false;
    }

    if (StringUtils.isEmpty(proofHeader)
        || StringUtils.isEmpty(oldProofHeader)
        || !StringUtils.isNumeric(timestampHeader)) {
      log.warn("request proof headers unavailable or incorrect");
      return false;
    }

    boolean timestampValid = verifyRequestTimestamp(timestampHeader);
    if (!timestampValid) {
      log.warn("incorrect timestamp - must be within 20 minutes but was {}", timestampHeader);
      return false;
    }

    boolean proofKeyValid = false;
    byte[] expectedProof = getRequestedProofBytes(accessToken, requestUrl, timestampHeader);

    // verify X-WOPI-Proof value using the current public key
    try {
      proofKeyValid =
          verifyProofKey(
              wopiPublicKeys.getModulus(),
              wopiPublicKeys.getExponent(),
              proofHeader,
              expectedProof);
    } catch (GeneralSecurityException e) {
      log.warn("exception during proof key validation", e);
    }
    // verify X-WOPI-ProofOld value using the current public key
    if (!proofKeyValid) {
      try {
        proofKeyValid =
            verifyProofKey(
                wopiPublicKeys.getModulus(),
                wopiPublicKeys.getExponent(),
                oldProofHeader,
                expectedProof);
      } catch (GeneralSecurityException e) {
        log.warn("exception during proof key validation", e);
      }
    }
    // verify X-WOPI-Proof value using the old public key
    if (!proofKeyValid) {
      try {
        proofKeyValid =
            verifyProofKey(
                wopiPublicKeys.getOldModulus(),
                wopiPublicKeys.getOldExponent(),
                proofHeader,
                expectedProof);
      } catch (GeneralSecurityException e) {
        log.warn("exception during proof key validation", e);
      }
    }

    if (!proofKeyValid) {
      log.info(
          "proof key headers rejected for input: {}, {}, {}, {}, {}",
          accessToken,
          requestUrl,
          proofHeader,
          oldProofHeader,
          timestampHeader);
    }
    return proofKeyValid;
  }

  private static final long TWENTY_MINUTES_IN_TICKS = 20 * 60 * 10_000_000L;

  protected boolean verifyRequestTimestamp(String timestampHeader) {
    if (!timestampVerificationEnabled) {
      return true; // skip timestamp verification
    }

    // "Ensure that the X-WOPI-TimeStamp header is no more than 20 minutes old"
    long requestTimestampInTicks = Long.parseLong(timestampHeader);
    long curentTimestampInTicks =
        Duration.between(Instant.parse("0001-01-01T00:00:00.00Z"), Instant.now()).toMillis()
            * 10000;

    return curentTimestampInTicks - requestTimestampInTicks < TWENTY_MINUTES_IN_TICKS;
  }

  protected byte[] getRequestedProofBytes(String accessToken, String requestUrl, String timestamp) {
    byte[] accessTokenBytes = accessToken.getBytes(StandardCharsets.UTF_8);
    byte[] requestUrlUpperCaseBytes = requestUrl.toUpperCase().getBytes(StandardCharsets.UTF_8);
    Long timestampVal = Long.valueOf(timestamp);

    ByteBuffer byteBuffer =
        ByteBuffer.allocate(
            4 + accessTokenBytes.length + 4 + requestUrlUpperCaseBytes.length + 4 + 8);
    byteBuffer.putInt(accessTokenBytes.length);
    byteBuffer.put(accessTokenBytes);
    byteBuffer.putInt(requestUrlUpperCaseBytes.length);
    byteBuffer.put(requestUrlUpperCaseBytes);
    byteBuffer.putInt(8);
    byteBuffer.putLong(timestampVal);

    return byteBuffer.array();
  }

  protected boolean verifyProofKey(
      String strModulus, String strExponent, String strWopiProofKey, byte[] expectedProofArray)
      throws GeneralSecurityException {

    PublicKey publicKey = getPublicKey(strModulus, strExponent);
    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(publicKey);
    verifier.update(expectedProofArray);

    final byte[] signedProof = DatatypeConverter.parseBase64Binary(strWopiProofKey);
    return verifier.verify(signedProof);
  }

  private RSAPublicKey getPublicKey(String modulus, String exponent)
      throws GeneralSecurityException {
    BigInteger mod = new BigInteger(1, DatatypeConverter.parseBase64Binary(modulus));
    BigInteger exp = new BigInteger(1, DatatypeConverter.parseBase64Binary(exponent));
    KeyFactory factory = KeyFactory.getInstance("RSA");
    KeySpec ks = new RSAPublicKeySpec(mod, exp);

    return (RSAPublicKey) factory.generatePublic(ks);
  }

  /*
   * ============
   *  for tests
   * ============
   */

  void setTimestampVerificationEnabled(boolean timestampVerificationEnabled) {
    this.timestampVerificationEnabled = timestampVerificationEnabled;
  }
}
