package com.researchspace.export.pdf;

import lombok.Data;

@Data
public  class StoichiometryTableData {

  public StoichiometryTableData(
      String name,
      String role,
      Boolean limitingReagent,
      Double coefficient,
      Double molecularWeight,
      Double mass,
      Double moles,
      Double actualAmount,
      Double actualMoles,
      Double actualYield,
      String notes) {
    this.name = name;
    this.role = role;
    this.limitingReagent = limitingReagent != null ? limitingReagent.toString() : "false";
    this.coefficient = coefficient != null ? coefficient.toString() : "0";
    this.molecularWeight = molecularWeight != null ? molecularWeight.toString() : "0";
    this.mass = mass != null ? mass.toString() : "0";
    this.moles = moles != null ? moles.toString() : "0";
    this.actualAmount = actualAmount != null ? actualAmount.toString() : "0";
    this.actualMoles = actualMoles != null ? actualMoles.toString() : "0";
    this.actualYield = actualYield != null ? actualYield.toString() : "0";
    this.notes = notes != null ? notes : "";
  }

  public static Double calculateMoles(Double mass, Double molecularWeight) {
    if (mass == null || molecularWeight == null || molecularWeight <= 0) {
      return null;
    }
    return mass / molecularWeight;
  }

  private String name;
  private String role;
  private String limitingReagent;
  private String coefficient;
  private String molecularWeight;
  private String mass;
  private String moles;
  private String actualAmount;
  private String actualMoles;
  private String actualYield;
  private String notes;
}
