package com.researchspace.documentconversion.ext;

@FunctionalInterface
public interface ConversionChecker {
  boolean supportsConversion(String fromFormat, String toFormat);
}
