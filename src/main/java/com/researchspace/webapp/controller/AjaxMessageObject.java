package com.researchspace.webapp.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An object to return for ajax calls resulting in server message (e.g. errors). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AjaxMessageObject {

  private String message;
}
