package com.researchspace.api.v1.controller;

import org.springframework.util.MultiValueMap;

public abstract class ApiSearchConfig {

  public abstract MultiValueMap<String, String> toMap();
}
