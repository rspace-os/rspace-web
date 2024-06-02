package com.researchspace.ldap.impl;

import com.researchspace.model.User;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.support.LdapUtils;

@Slf4j
public class UserAttributeMapper implements AttributesMapper<User> {

  private String ldapAttrNameForUsername;
  private String ldapAttrNameForFullDn;
  private String ldapAttrNameForSid;

  /**
   * Configures the mapper with names of the attributes that will be mapped to particular fields of
   * User object.
   *
   * @param usernameAttrName (required) name of the attribute mapped to user.username
   * @param dnAttrName (optional) name of the attribute mapped to user.token field
   * @param sidAttrName (optional) name of the attribute mapped to user.sid field
   */
  public UserAttributeMapper(String usernameAttrName, String dnAttrName, String sidAttrName) {
    Validate.notBlank(usernameAttrName);
    ldapAttrNameForUsername = usernameAttrName;
    ldapAttrNameForFullDn = dnAttrName;
    ldapAttrNameForSid = sidAttrName;
  }

  @Override
  public User mapFromAttributes(Attributes attrs) throws NamingException {
    User user = new User();

    String username = (String) attrs.get(ldapAttrNameForUsername).get();
    log.info("retrieved username from ldap.userSearchQuery.uidField - value '{}'", username);
    user.setUsername(username);

    Attribute fullDnAttr = attrs.get(ldapAttrNameForFullDn);

    String foundUserDn = null;
    if (fullDnAttr != null) {
      foundUserDn = (String) fullDnAttr.get();
      log.info("retrieved dn from ldap.userSearchQuery.dnField - value '{}'", foundUserDn);
    }
    /* full dn is required for authentication, save it in 'token' field from the lack of better place */
    user.setToken(foundUserDn);

    if (StringUtils.isNotBlank(ldapAttrNameForSid)) {
      Attribute sidAttr = attrs.get(ldapAttrNameForSid);
      if (sidAttr != null) {
        log.info(
            "retrieved sid from ldap.userSearchQuery.objectSidField - value '{}'", sidAttr.get());
        String sid = LdapUtils.convertBinarySidToString((byte[]) sidAttr.get());
        user.setSid(sid);
      }
    }

    Attribute givenNameAttr = attrs.get("givenName");
    if (givenNameAttr != null) {
      user.setFirstName((String) givenNameAttr.get());
    }

    Attribute lastNameAttr = attrs.get("sn");
    if (lastNameAttr != null) {
      user.setLastName((String) lastNameAttr.get());
    }

    Attribute emailAttr = attrs.get("mail");
    if (emailAttr != null) {
      user.setEmail((String) emailAttr.get());
    }
    log.info(
        "Recovered user attributes: firstname={}, lastname={}, email={}",
        user.getFirstName(),
        user.getLastName(),
        user.getEmail());
    return user;
  }
}
