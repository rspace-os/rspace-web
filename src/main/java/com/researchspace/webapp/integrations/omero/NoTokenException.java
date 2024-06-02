package com.researchspace.webapp.integrations.omero;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// UI code maps 401 exceptions to an appropriate 'error in token message' that will prompt user to
// reconnect to the App
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class NoTokenException extends RuntimeException {}
