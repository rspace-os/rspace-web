package com.researchspace.model.events;

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
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Event log of UserAccount - related actions. This is a table rather than in the audit log as this
 * information will be queried more frequently (in group and profile pages), will be quite
 * low-volume and therefore will be more efficient than parsing log files.
 */
@Entity
@NoArgsConstructor
@EqualsAndHashCode(of = {"user", "timestamp", "accountEventType"})
@ToString
public class UserAccountEvent implements Serializable {

  /** */
  private static final long serialVersionUID = -9065064321226583664L;

  /*
   * Package scoped for testing
   */
  UserAccountEvent(Long id, User user, AccountEventType accountEventType, Date timestamp) {
    super();
    this.id = id;
    this.user = user;
    this.timestamp = timestamp;
    this.accountEventType = accountEventType;
  }

  /**
   * Public constructor sets timestamp internally
   *
   * @param user
   * @param accountEventType
   */
  public UserAccountEvent(@NotNull User user, @NotNull AccountEventType accountEventType) {
    this(null, user, accountEventType, new Date());
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "user_account_event_gen")
  @TableGenerator(
      name = "user_account_event_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  @Getter
  @Setter(AccessLevel.PACKAGE)
  private Long id;

  @ManyToOne(optional = false)
  @Getter
  @Setter
  @NotNull
  private User user;

  @CreationTimestamp()
  @Getter
  @Setter(AccessLevel.PRIVATE) // for hibernate
  @Column(nullable = false)
  @NotNull
  private Date timestamp;

  @Enumerated(EnumType.STRING)
  @Getter
  @Setter
  @Column(nullable = false)
  @NotNull
  private AccountEventType accountEventType;
}
