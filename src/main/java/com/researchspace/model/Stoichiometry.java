package com.researchspace.model;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
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
 * Entity class representing a stoichiometry table for a chemical reaction. Contains information
 * about the reaction and its molecules.
 */
@Entity
@Audited
@Table(name = "Stoichiometry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = "molecules")
public class Stoichiometry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_reaction_id", nullable = false)
  private RSChemElement parentReaction;

  @Column(name = "formula")
  private String formula;

  @Column(name = "is_reaction")
  private boolean isReaction;

  @Column(name = "additional_metadata")
  @Type(type = "text")
  private String additionalMetadata;

  @Builder.Default
  @OneToMany(mappedBy = "stoichiometry", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<StoichiometryMolecule> molecules = new ArrayList<>();

  /**
   * Add a molecule to this stoichiometry table.
   *
   * @param molecule the molecule to add
   * @return the added molecule
   */
  public StoichiometryMolecule addMolecule(StoichiometryMolecule molecule) {
    molecules.add(molecule);
    molecule.setStoichiometry(this);
    return molecule;
  }

  /**
   * Remove a molecule from this stoichiometry table.
   *
   * @param molecule the molecule to remove
   */
  public void removeMolecule(StoichiometryMolecule molecule) {
    molecules.remove(molecule);
    molecule.setStoichiometry(null);
  }
}
