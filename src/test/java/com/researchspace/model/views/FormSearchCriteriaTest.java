package com.researchspace.model.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FormSearchCriteriaTest {

    private FormSearchCriteria sc;

    @Before
    public void setUp() throws Exception {
        sc = new FormSearchCriteria();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testCanSetValidSearchTerm() {
        sc.setSearchTerm(null);
        sc.setSearchTerm("");
        sc.setSearchTerm(" a valid name");
        sc.setSearchTerm(" user's name");
    }

    @Test
    public void testSearchFieldC() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        assertEquals(2, sc.getSearchTermField2Values().keySet().size());
        sc.setSearchTerm("anyname");
        assertEquals(3, sc.getSearchTermField2Values().keySet().size());
        assertFalse(sc.getURLQueryString().endsWith("&"));
        assertTrue(sc.getURLQueryString().contains("anyname"));
    }

}
