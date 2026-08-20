package com.researchspace.model.record;

import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

public class SnippetTest {

    private Snippet snippet;

    @Before
    public void setUp() {
        snippet = new Snippet();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetSetNameCantBeEmptyName() {
        snippet.setName(" ");
    }

    @Test
    public void testInititalProperties() {
        snippet = new Snippet();
        assertNotNull(snippet.getModificationDate());
        assertNotNull(snippet.getCreationDate());
    }

}
