package com.researchspace.export.stoichiometry;

import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import lombok.Getter;

@Getter
public class StoichiometryTableData {
  public static String serverPrefix = "";
  private String inventoryLinkedItem;
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

  private StoichiometryTableData(
      String inventoryLinkedItem,
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
    this.inventoryLinkedItem = inventoryLinkedItem != null ? inventoryLinkedItem : "-";
    this.name = name != null ? name : "UNKNOWN";
    this.role = role;
    this.limitingReagent = limitingReagent != null ? limitingReagent.toString() : "false";
    this.coefficient = coefficient != null ? coefficient.toString() : "0";
    this.molecularWeight = roundToThreeDecimals(molecularWeight);
    this.mass = roundToThreeDecimals(mass);
    this.moles = roundToThreeDecimals(moles);
    this.actualAmount = roundToThreeDecimals(actualAmount);
    this.actualMoles = roundToThreeDecimals(actualMoles);
    this.actualYield = roundToThreeDecimals(actualYield);
    this.notes = notes != null ? notes : "";
  }

  public StoichiometryTableData(StoichiometryMoleculeDTO moleculeDTO) {
    this(
        readInventoryItemGlobalId(moleculeDTO),
        moleculeDTO.getName(),
        moleculeDTO.getRole().name(),
        moleculeDTO.getLimitingReagent(),
        moleculeDTO.getCoefficient(),
        moleculeDTO.getMolecularWeight(),
        moleculeDTO.getMass(),
        calculateMoles(moleculeDTO.getMass(), moleculeDTO.getMolecularWeight()),
        moleculeDTO.getActualAmount(),
        calculateMoles(moleculeDTO.getActualAmount(), moleculeDTO.getMolecularWeight()),
        moleculeDTO.getActualYield(),
        moleculeDTO.getNotes());
  }

  public String getServerPrefix() {
    return serverPrefix;
  }

  private static String readInventoryItemGlobalId(StoichiometryMoleculeDTO moleculeDTO) {
    if (moleculeDTO.getInventoryLink() == null) {
      return null;
    }
    return moleculeDTO.getInventoryLink().getInventoryItemGlobalId();
  }

  public static String roundToThreeDecimals(Double value) {
    if (value == null) {
      return "0";
    }
    DecimalFormat df = new DecimalFormat("#.###");
    df.setRoundingMode(RoundingMode.HALF_UP);
    return df.format(value);
  }

  public static Double calculateMoles(Double mass, Double molecularWeight) {
    if (mass == null || molecularWeight == null || molecularWeight <= 0) {
      return null;
    }
    return mass / molecularWeight;
  }
}
