package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.properties.PropertyHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;

@ExtendWith(MockitoExtension.class)
public class PropertyHolderTest {
  @Mock ResourceLoader mockResourceLoader;
  PropertyHolder holder = new PropertyHolder();

  @BeforeEach
  public void setUp() throws Exception {
    holder = new PropertyHolder();
    holder.setResourceLoader(mockResourceLoader);
  }

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

  @Test
  public void testGetDigitalCommonsDataProperties() {
    holder.setDigitalCommonsDataBaseUrl("https://baseurl.com");

    assertEquals("https://baseurl.com", holder.getDigitalCommonsDataBaseUrl());
  }

  @Test
  public void testGetFieldmakrProperties() {
    holder.setFieldmarkBaseUrl("https://base.fieldmark.com");

    assertEquals("https://base.fieldmark.com", holder.getFieldmarkBaseUrl());
  }
}
