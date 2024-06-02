package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;

import com.researchspace.properties.PropertyHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;

public class PropertyHolderTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock ResourceLoader mockResourceLoader;
  PropertyHolder holder = new PropertyHolder();

  @Before
  public void setUp() throws Exception {
    holder = new PropertyHolder();
    holder.setResourceLoader(mockResourceLoader);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testGetBannerImageName() {
    Mockito.when(mockResourceLoader.getResource(Mockito.anyString()))
        .thenReturn(new FileSystemResource("any"));
    holder.setBannerImagePath("file://a/b/c.png");
    assertEquals("c.png", holder.getBannerImageName());
    holder.setBannerImagePath("c.png");
    assertEquals("c.png", holder.getBannerImageName());
    holder.setBannerImagePath("/c.png");
    assertEquals("c.png", holder.getBannerImageName());
    holder.setBannerImagePath("/c.png/");
    assertEquals("c.png", holder.getBannerImageName());
    holder.setBannerImagePath("");
    assertEquals("unknown", holder.getBannerImageName());
  }
}
