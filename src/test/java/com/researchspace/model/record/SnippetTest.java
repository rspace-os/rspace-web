package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SnippetTest {

  private Snippet snippet;

  @BeforeEach
  public void setUp() {
    snippet = new Snippet();
  }

  @Test
  public void testGetSetNameCantBeEmptyName() {
    assertThrows(IllegalArgumentException.class, () -> snippet.setName(" "));
  }

  @Test
  public void testInititalProperties() {
    snippet = new Snippet();
    assertNotNull(snippet.getModificationDate());
    assertNotNull(snippet.getCreationDate());
  }
}
