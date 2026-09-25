package com.researchspace.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class ArchiveModelTest {

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
