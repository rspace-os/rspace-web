package com.researchspace.model;

import com.researchspace.core.util.SecureStringUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.Date;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TokenBasedVerification implements Serializable {

  private static final long serialVersionUID = -697456064271544085L;
  private Long id;
  private User user;
  private String email;
  private Date requestTime;
  private String token;
  private boolean resetCompleted = false;
  private String ipAddressOfRequestor;
  private TokenBasedVerificationType verificationType;

  /**
   * Public constructor.
   *
   * @param email
   * @param requestTime an optional date when the request is timed from.
   * @param requestTime
   */
  public TokenBasedVerification(String email, Date requestTime, TokenBasedVerificationType type) {
    if (email == null) {
      throw new IllegalArgumentException("Can't have null user");
    }
    if (type == null) {
      throw new IllegalArgumentException("Can't have null type");
    }

    this.email = email;
    this.requestTime = requestTime;
    this.verificationType = type;
    if (this.requestTime == null) {
      this.requestTime = new Date();
    }
    setToken(SecureStringUtils.getURLSafeSecureRandomString(16));
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "token_based_verification_gen")
  @TableGenerator(
      name = "token_based_verification_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  void setId(Long id) {
    this.id = id;
  }

  @ManyToOne
  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getEmail() {
    return email;
  }

  void setEmail(String email) {
    this.email = email;
  }

  /**
   * Boolean test that the token has been used and is now invalid (it cannot be used twice).
   *
   * @return
   */
  public boolean isResetCompleted() {
    return resetCompleted;
  }

  public void setResetCompleted(boolean resetCompleted) {
    this.resetCompleted = resetCompleted;
  }

  @Temporal(TemporalType.TIMESTAMP)
  public Date getRequestTime() {
    return requestTime;
  }

  void setRequestTime(Date requestTime) {
    this.requestTime = requestTime;
  }

  @Column(length = 24, nullable = false, unique = true)
  public String getToken() {
    return token;
  }

  void setToken(String token) {
    this.token = token;
  }

  /**
   * The IP address of the client requesting the verification.
   *
   * @return
   */
  public String getIpAddressOfRequestor() {
    return ipAddressOfRequestor;
  }

  public void setIpAddressOfRequestor(String ipAddressOfRequestor) {
    this.ipAddressOfRequestor = ipAddressOfRequestor;
  }

  @Enumerated(EnumType.ORDINAL)
  public TokenBasedVerificationType getVerificationType() {
    return verificationType;
  }

  void setVerificationType(TokenBasedVerificationType verificationType) {
    this.verificationType = verificationType;
  }

  @Override
  public String toString() {
    return "TokenBasedVerification [email="
        + email
        + ", requestTime="
        + requestTime
        + ", token="
        + token
        + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((email == null) ? 0 : email.hashCode());
    result = prime * result + ((requestTime == null) ? 0 : requestTime.hashCode());
    result = prime * result + ((token == null) ? 0 : token.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    TokenBasedVerification other = (TokenBasedVerification) obj;
    if (email == null) {
      if (other.email != null) return false;
    } else if (!email.equals(other.email)) return false;
    if (requestTime == null) {
      if (other.requestTime != null) return false;
    } else if (!requestTime.equals(other.requestTime)) return false;
    if (token == null) {
      if (other.token != null) return false;
    } else if (!token.equals(other.token)) return false;
    return true;
  }

  /**
   * Activation link is valid if argument token = this token and is within the active period, and
   * the token type matches.
   *
   * @param testToken
   * @param tokenType The expected token type
   * @return
   */
  @Transient
  public boolean isValidLink(String testToken, TokenBasedVerificationType tokenType) {
    return !isResetCompleted()
        && this.token.equals(testToken)
        && this.verificationType.equals(tokenType)
        && new Date().getTime() - requestTime.getTime() < this.verificationType.getTimeout();
  }
}
