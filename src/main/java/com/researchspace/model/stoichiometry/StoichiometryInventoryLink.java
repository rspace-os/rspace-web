package com.researchspace.model.stoichiometry;

import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecordConnectedEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.ConstraintViolationException;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Entity
@Access(AccessType.PROPERTY)
@Setter
@NoArgsConstructor
@Audited
public class StoichiometryInventoryLink extends InventoryRecordConnectedEntity {

  private Long id;
  private StoichiometryMolecule stoichiometryMolecule;
  private boolean stockDeducted = false;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long getId() {
    return id;
  }

  @OneToOne
  @JoinColumn(name = "stoichiometry_molecule_id", nullable = false)
  public StoichiometryMolecule getStoichiometryMolecule() {
    return stoichiometryMolecule;
  }

  @Column(name = "stock_deducted", nullable = false)
  public boolean isStockDeducted() {
    return stockDeducted;
  }

  @Override
  public void validateBeforeSave() {
    super.validateBeforeSave();
    // isSampleTemplate() rather than instanceof: a lazily-loaded reference is a proxy typed to
    // the abstract root (never the concrete subclass), but virtual dispatch reaches the real type
    InventoryRecord invRec = getInventoryRecord();
    if (invRec != null && invRec.isSampleTemplate()) {
      throw new ConstraintViolationException("Cannot link stoichiometry to sample template", null);
    }
  }
}

