package com.researchspace.webapp.controller;

import com.researchspace.model.RecordGroupSharing;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

class PublicDocumentsUtilities {

  public static void loginAnonymousUser(String password) {
    String uName = RecordGroupSharing.ANONYMOUS_USER;
    UsernamePasswordToken token = new UsernamePasswordToken(uName, password, false);
    final Subject subject = SecurityUtils.getSubject();
    subject.login(token);
    // Prevents race condition where loading public documents makes requests for the public images
    // but Shiro has not yet accepted that the
    // anonymous user has logged in. If this happens, image links on the public document are broken
    // but will fix on any refresh and any subsequent
    // loading of documents in that browser instance will work (because the anonymous user will have
    // already logged in).
    try {
      TimeUnit.SECONDS.sleep(1);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
