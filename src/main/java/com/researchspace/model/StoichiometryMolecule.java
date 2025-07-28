package com.researchspace.model;

import com.researchspace.model.dtos.chemistry.MoleculeRole;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.envers.Audited;

/**
 * Entity class representing a molecule in a stoichiometry table. Contains information about the
 * molecule's properties and role in the reaction.
 */
@Entity
@Audited
@Table(name = "StoichiometryMolecule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "stoichiometry")
public class StoichiometryMolecule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stoichiometry_id", nullable = false)
  private Stoichiometry stoichiometry;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "molecule_id", nullable = false)
  private RSChemElement molecule;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private MoleculeRole role;

  @Column(name = "compound")
  private String compound;

  @Column(name = "coefficient")
  private Double coefficient;

  @Column(name = "molecular_mass")
  private Double molecularMass;

  @Column(name = "absolute_mass")
  private Double absoluteMass;

  @Column(name = "volume")
  private Double volume;

  @Column(name = "used_expected_amount")
  private Double usedExpectedAmount;

  @Column(name = "actual_stoichiometry")
  private Double actualStoichiometry;

  @Column(name = "actual_yield")
  private Double actualYield;

  @Column(name = "yield_percentage")
  private Double yieldPercentage;

  @Column(name = "additional_metadata")
  @Type(type = "text")
  private String additionalMetadata;
}
