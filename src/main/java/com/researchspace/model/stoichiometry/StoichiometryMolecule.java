package com.researchspace.model.stoichiometry;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.researchspace.model.RSChemElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "StoichiometryMolecule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id", "rsChemElement"})
@ToString(exclude = "stoichiometry")
@Audited
public class StoichiometryMolecule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "stoichiometry_id", nullable = false)
  @JsonBackReference
  private Stoichiometry stoichiometry;

  @OneToOne
  @Fetch(FetchMode.SELECT)
  @JoinColumn(name = "rs_chem_id", nullable = false)
  private RSChemElement rsChemElement;

  @OneToOne(mappedBy = "stoichiometryMolecule", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
  private StoichiometryInventoryLink inventoryLink;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MoleculeRole role;

  @Column private String formula;

  @Column private String name;

  @Column(columnDefinition = "text")
  private String smiles;

  @Builder.Default @Column private Double coefficient = 1.00;

  @Column(name = "molecular_weight")
  private Double molecularWeight;

  @Column private Double mass;

  @Column(name = "actual_amount")
  private Double actualAmount;

  @Column(name = "actual_yield")
  private Double actualYield;

  @Column(name = "limiting_reagent")
  private Boolean limitingReagent;

  @Column(columnDefinition = "text")
  private String notes;
}

