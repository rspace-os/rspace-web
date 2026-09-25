package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.SubnetUtils.SubnetInfo;
import org.junit.jupiter.api.Test;

/** Usage testing; src code is taken from Apache commons net project */
public class SubnetUtilsTest {

  private static final String VALID_IP = "123.222.111.192";
  private static final String VALID_CIDR = "123.222.111.192/27";

  @Test
  public void test() {
    String validCidr = VALID_CIDR;
    SubnetUtils utils = new SubnetUtils(validCidr);
    utils.setInclusiveHostCount(true);
    SubnetInfo info = utils.getInfo();
    assertEquals(VALID_IP, info.getLowAddress());
    assertEquals("123.222.111.223", info.getHighAddress());
  }

  @Test
  public void testInvalid() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new SubnetUtils(VALID_IP); // must be cidr
        });
  }

  @Test
  public void testIsIp4OrCidr() {
    assertTrue(SubnetUtils.isValidIp4OrCIDRAddress(VALID_CIDR));
    assertTrue(SubnetUtils.isValidIp4OrCIDRAddress(VALID_IP));
  }

  @Test
  public void testIsCidr() {
    assertTrue(SubnetUtils.isValidCIDRAddress(VALID_CIDR));
    assertFalse(SubnetUtils.isValidCIDRAddress(VALID_IP));
  }
}
