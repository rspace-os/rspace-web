package com.researchspace.auth.password;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NewPasswordEncodeGateTest {

  @Test
  void admitsUpToThePermitCountThenRefusesAtOnce() {
    NewPasswordEncodeGate gate = new NewPasswordEncodeGate(2);
    assertTrue(gate.tryAcquire());
    assertTrue(gate.tryAcquire());
    assertFalse(gate.tryAcquire());

    gate.release();
    assertTrue(gate.tryAcquire());
    assertFalse(gate.tryAcquire());
  }

  @Test
  void needsAtLeastOnePermit() {
    assertThrows(IllegalArgumentException.class, () -> new NewPasswordEncodeGate(0));
  }
}
