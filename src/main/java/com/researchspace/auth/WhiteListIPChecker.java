package com.researchspace.auth;

import com.researchspace.model.User;
import javax.servlet.ServletRequest;
import org.slf4j.Logger;

public interface WhiteListIPChecker {

  boolean isRequestWhitelisted(ServletRequest request, User subject, Logger securityLog);
}
