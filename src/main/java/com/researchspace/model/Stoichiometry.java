package com.researchspace.model;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
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

@Entity
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

  @ManyToOne
  @JoinColumn(name = "parent_reaction_id", nullable = false)
  private RSChemElement parentReaction;

  @Builder.Default
  @OneToMany(
      mappedBy = "stoichiometry",
      cascade = CascadeType.ALL,
      fetch = javax.persistence.FetchType.EAGER)
  private List<StoichiometryMolecule> molecules = new ArrayList<>();

  public void addMolecule(StoichiometryMolecule molecule) {
    molecules.add(molecule);
    molecule.setStoichiometry(this);
  }
}
