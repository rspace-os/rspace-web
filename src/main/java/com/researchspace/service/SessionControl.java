package com.researchspace.service;

import org.apache.shiro.realm.Realm;

public interface SessionControl extends Realm {

  public void setIgnoreSession(boolean ignoreSession);
}
