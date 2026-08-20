package com.researchspace.model.stoichiometry;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.record.Record;
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
@Table(name = "Stoichiometry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "uuid")
@ToString(exclude = "molecules")
@Audited
public class Stoichiometry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // there's no natural key for ensuring uniqueness before Stoichiometry entities are persisted to the db,
  // therefore use this UUID. We need to ensure uniqueness in the case where Stoichiometry entities are
  // exported to another rspace instance before they're persisted.
  @Builder.Default
  @Column(unique = true, nullable = false, length = 36)
  private String uuid = UUID.randomUUID().toString();

  // SELECT, not the default JOIN: the eager `molecules` collection below is join-fetched, and the
  // owning Record has its own eagerly-reachable folder-membership collection (RecordToFolder). If
  // these to-one associations were join-fetched in the same query, that collection would join too
  // and produce a `molecules x folderMemberships` Cartesian product, so once the document is shared
  // (and therefore lives in more than one folder) every molecule row is returned duplicated. Loading
  // these to-one associations via a separate select keeps them out of the molecules join. The
  // persisted rows are unaffected; this only governs how the eager graph is read back.
  @ManyToOne
  @Fetch(FetchMode.SELECT)
  @JoinColumn(name = "parent_reaction_id", nullable = true)
  private RSChemElement parentReaction;

  @ManyToOne
  @Fetch(FetchMode.SELECT)
  @JoinColumn(name = "record_id", nullable = false)
  private Record record;

  @Builder.Default
  @OneToMany(
          mappedBy = "stoichiometry",
          cascade = CascadeType.ALL,
          orphanRemoval = true,
          fetch = jakarta.persistence.FetchType.EAGER)
  private List<StoichiometryMolecule> molecules = new ArrayList<>();

  @Column(name = "last_modified")
  @Temporal(TemporalType.TIMESTAMP)
  private Date lastModified;

  public void addMolecule(StoichiometryMolecule molecule) {
    molecules.add(molecule);
    molecule.setStoichiometry(this);
  }

  @PrePersist
  @PreUpdate
  private void updateTimestamp() {
    this.lastModified = new Date();
    if (this.uuid == null) {
      this.uuid = UUID.randomUUID().toString();
    }
  }

  /*
  Update the lastModified timestamp for audit purposes when a child StoichiometryMolecule is updated
   */
  public void touchForAudit() {
    this.lastModified = new Date();
  }
}
