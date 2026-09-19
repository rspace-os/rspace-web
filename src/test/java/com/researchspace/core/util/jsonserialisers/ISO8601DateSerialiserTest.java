package com.researchspace.core.util.jsonserialisers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ISO8601DateSerialiserTest {

  ISO8601DateSerialiser dateSerialiser;

  @BeforeEach
  public void setUp() throws Exception {
    dateSerialiser = new ISO8601DateSerialiser();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void getDateString() {
    final long TIME_IN_MILLIS = 1521590400000L;
    assertEquals("2018-03-21", dateSerialiser.getDateString(TIME_IN_MILLIS));
  }
}
