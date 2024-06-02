package com.researchspace.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchiveModelTest {

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testParseProperties() {
    Pattern p = ArchiveModel.MANIFEST_PATTERN;
    String okcolon = "Key:123456-789";
    Matcher m = p.matcher(okcolon);
    assertTrue(m.matches());
    assertEquals("Key", m.group(1));
    assertEquals("123456-789", m.group(2));

    String multicolon = "Key:123456:789";
    m = p.matcher(multicolon);
    assertTrue(m.matches());
    assertEquals("Key", m.group(1));
    assertEquals("123456:789", m.group(2));
  }
}
