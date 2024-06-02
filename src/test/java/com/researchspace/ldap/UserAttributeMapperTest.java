package com.researchspace.ldap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.ldap.impl.UserAttributeMapper;
import com.researchspace.model.User;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import org.junit.Test;

public class UserAttributeMapperTest {

  @Test
  public void testLdapAttributeMapping() throws Exception {

    // standard mapper with fallback dn calculation disabled
    UserAttributeMapper testMapper = new UserAttributeMapper("uid", "dn", "");

    // dn attr present
    Attributes attrs1 = new BasicAttributes();
    attrs1.put("uid", "u1");
    attrs1.put("dn", "dn=u1");
    User mappedUser = testMapper.mapFromAttributes(attrs1);
    assertEquals("u1", mappedUser.getUsername());
    assertEquals("dn=u1", mappedUser.getToken());

    // dn attr absent
    Attributes attrs2 = new BasicAttributes();
    attrs2.put("uid", "u1");
    mappedUser = testMapper.mapFromAttributes(attrs2);
    assertEquals("u1", mappedUser.getUsername());
    assertEquals(null, mappedUser.getToken());
  }
}
