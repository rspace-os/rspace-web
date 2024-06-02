package com.researchspace.webapp.integrations.protocolsio;

import static org.junit.Assert.*;

import org.junit.Test;

public class ResponseParser {

  @Test
  public void test() {
    int[] codes =
        new int[] {
          123, 34, 115, 116, 97, 116, 117, 115, 95, 99, 111, 100, 101, 34, 58, 49, 50, 49, 57, 44,
          34, 101, 114, 114, 111, 114, 95, 109, 101, 115, 115, 97, 103, 101, 34, 58, 34, 116, 111,
          107, 101, 110, 32, 105, 115, 32, 101, 120, 112, 105, 114, 101, 100, 34, 125
        };
    StringBuilder sb = new StringBuilder();
    for (int code : codes) {
      sb.append((char) code);
    }
    System.err.println(sb.toString());
  }
}
