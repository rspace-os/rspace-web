package com.researchspace.model.dmps;

import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Join table for User to DMP, storing date of import. */
@Entity
@Data
@NoArgsConstructor
public class DMPUser {

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "dmp_user_gen")
  @TableGenerator(
      name = "dmp_user_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  private Long id;

  void setId(Long id) {
    this.id = id;
  }

  @ManyToOne(optional = false)
  private User user;

  @Column(nullable = false)
  private Instant timestamp;

  @ManyToOne private EcatDocumentFile dmpDownloadFile;

  @Enumerated(EnumType.STRING)
  private DMPSource source;

  private String doiLink;
  private String dmpLink;
  private String dmpId;
  private String title;

  public DMPUser(User user, DmpDto dmpDto) {
    this.user = user;
    this.dmpId = dmpDto.getDmpId();
    this.title = dmpDto.getTitle();
    this.source = dmpDto.getSource();
    this.dmpLink = dmpDto.getDmpLink();
    this.doiLink = dmpDto.getDoiLink();
    this.timestamp = Instant.now();
  }
}
