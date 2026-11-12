package com.researchspace.booking.api.v2;

import com.researchspace.api.v2.controller.ApiV2VersionPreconditionAdvice;
import org.springframework.web.bind.annotation.ControllerAdvice;

/** Applies the shared body-version precondition tracking to booking controllers. */
@ControllerAdvice(basePackages = "com.researchspace.booking.api.v2")
public class BookingApiV2VersionPreconditionAdvice extends ApiV2VersionPreconditionAdvice {}
