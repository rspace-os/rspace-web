package com.axiope.service.cfg;

import com.researchspace.documentconversion.ext.CustomerIDSupplier;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.service.impl.PDFToImageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocConverterBaseConfig {

  @Bean
  public DocumentConversionService pdfToImageConverter() {
    return new PDFToImageConverter();
  }

  @Bean
  public CustomerIDSupplier customerIDSupplier() {
    return new CustomerIDSupplier();
  }
}
