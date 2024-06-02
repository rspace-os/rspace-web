package com.researchspace.webapp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

public class PublicLinkNotFoundException extends HttpClientErrorException {

  public PublicLinkNotFoundException(String badLink) {
    super(HttpStatus.NOT_FOUND, badLink);
  }
}
