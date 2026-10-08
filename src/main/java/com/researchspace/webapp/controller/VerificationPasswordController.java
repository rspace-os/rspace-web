package com.researchspace.webapp.controller;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.ProductType;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.service.IVerificationPasswordValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/**
 * Handle requests to set and change a verification password, which is used to reauthenticate users
 * in an SSO environment when users' real logins are unknown. See RSPAC-1206.
 */
@Controller
@RequestMapping("/vfpwd")
@Product(value = {ProductType.SSO, ProductType.COMMUNITY})
public class VerificationPasswordController extends BaseController {

  private @Autowired UserValidator userValidator;
  private @Autowired IVerificationPasswordValidator verificationPasswordValidator;

  /**
   * How long an initial set waits for another in-flight set by the same user, normally one hash and
   * one save. The bound only matters if the database stalls.
   */
  @Setter(AccessLevel.PACKAGE) // for testing
  private Duration initialSetWait = Duration.ofSeconds(5);

  private final ConcurrentHashMap<String, InitialSetLock> initialSetLocks =
      new ConcurrentHashMap<>();

  private static class InitialSetLock {
    final ReentrantLock lock = new ReentrantLock();
    int holders;
  }

  @Autowired
  @Qualifier("verificationPasswordResetHandler")
  private PasswordChangeHandlerBase passwordResetHandler;

  @Autowired
  @Qualifier("verificationPasswordResetByEmailHandler")
  private PasswordResetByEmailHandlerBase passwordResetEmailHandler;

  @IgnoreInLoggingInterceptor(ignoreAllRequestParams = true)
  @PostMapping("/ajax/changeVerificationPassword")
  @ResponseBody
  public AjaxReturnObject<String> changeVerificationPassword(
      @RequestParam("currentVerificationPassword") String currentVerificationPassword,
      @RequestParam("newVerificationPassword") String newVerificationPassword,
      @RequestParam("confirmVerificationPassword") String confirmVerificationPassword,
      HttpServletRequest request) {
    User user = userManager.getAuthenticatedUserInSession();
    String msg =
        passwordResetHandler.changePassword(
            currentVerificationPassword,
            newVerificationPassword,
            confirmVerificationPassword,
            request,
            user);
    return new AjaxReturnObject<>(msg, null);
  }

  /** Sets a new verification password, when one has not been set. */
  @IgnoreInLoggingInterceptor(ignoreAllRequestParams = true)
  @PostMapping("/ajax/setVerificationPassword")
  @ResponseBody
  public AjaxReturnObject<String> setVerificationPassword(
      @RequestParam("newVerificationPassword") String newVerificationPassword,
      @RequestParam("confirmVerificationPassword") String confirmVerificationPassword,
      HttpServletRequest request) {

    User user = userManager.getAuthenticatedUserInSession();

    if (verificationPasswordValidator.isVerificationPasswordSet(user)) {
      SECURITY_LOG.warn(
          "User [{}] attempted to set verification password, from {}, but it has already been set",
          user.getUsername(),
          RequestUtil.remoteAddr(request));
      return new AjaxReturnObject<>(getText("verificationPassword.set.errors.alreadySet"), null);
    }

    String newPass = StringUtils.trim(newVerificationPassword);
    String confirmPass = StringUtils.trim(confirmVerificationPassword);

    if (isInputStringBlank(newPass) || isInputStringBlank(confirmPass)) {
      return new AjaxReturnObject<>(getText("errors.allFields.required"), null);
    }

    String checkPasswordResult =
        userValidator.validatePasswords(
            newVerificationPassword, confirmVerificationPassword, user.getUsername());
    if (!UserValidator.FIELD_OK.equals(checkPasswordResult)) {
      SECURITY_LOG.warn(
          "User [{}] unsuccessfully attempted to set verification password, from {}",
          user.getUsername(),
          RequestUtil.remoteAddr(request));
      return new AjaxReturnObject<>(checkPasswordResult, null);
    }

    // One initial set per user at a time, so one account cannot hold several Argon2 encodes and
    // a duplicate submission does not overwrite the first.
    String username = user.getUsername();
    InitialSetLock initialSetLock = acquireHolder(username);
    try {
      if (!initialSetLock.lock.tryLock(initialSetWait.toNanos(), TimeUnit.NANOSECONDS)) {
        SECURITY_LOG.warn(
            "User [{}] could not set verification password, from {}: another set is still running",
            username,
            RequestUtil.remoteAddr(request));
        return new AjaxReturnObject<>(getText("verificationPassword.set.errors.busy"), null);
      }
      try {
        User current = userManager.getUserByUsername(username, true);
        if (!verificationPasswordValidator.isVerificationPasswordSet(current)) {
          current.setVerificationPassword(
              verificationPasswordValidator.hashVerificationPassword(newPass));
          userManager.saveUser(current);
          SECURITY_LOG.info("User [{}] successfully set verification password", username);
        }
        return new AjaxReturnObject<>(getText("verificationPassword.set.success"), null);
      } finally {
        initialSetLock.lock.unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new AjaxReturnObject<>(getText("verificationPassword.set.errors.busy"), null);
    } finally {
      releaseHolder(username);
    }
  }

  private InitialSetLock acquireHolder(String username) {
    return initialSetLocks.compute(
        username,
        (k, existing) -> {
          InitialSetLock lock = existing == null ? new InitialSetLock() : existing;
          lock.holders++;
          return lock;
        });
  }

  private void releaseHolder(String username) {
    initialSetLocks.computeIfPresent(username, (k, lock) -> --lock.holders == 0 ? null : lock);
  }

  /**
   * This is used in all products to determine if verification password (VP) is required or not.
   * When a sysadmin is 'operating-as', this will assert that the <strong>sysadmin's </strong>VP is
   * set, <strong>not</strong> that of the original user.
   */
  @Product(value = {ProductType.SSO, ProductType.COMMUNITY, ProductType.STANDALONE})
  @IgnoreInLoggingInterceptor(ignoreAllRequestParams = true)
  @GetMapping("/ajax/checkVerificationPasswordNeeded")
  @ResponseBody
  public AjaxReturnObject<Boolean> checkVerificationPasswordNeeded() {
    User subject = userManager.getAuthenticatedUserInSession();
    subject = userManager.getOriginalUserForOperateAs(subject);

    return new AjaxReturnObject<>(
        !verificationPasswordValidator.isVerificationPasswordSet(subject), null);
  }

  /**
   * Posts an initial request to change a password.<br>
   * This method stores the request and notifies the requester by email
   */
  @PostMapping("/verificationPasswordResetRequest")
  public ModelAndView requestVerificationPasswordReset(HttpServletRequest request) {
    User user = userManager.getAuthenticatedUserInSession();
    String email = user.getEmail();

    passwordResetEmailHandler.sendChangeCredentialsEmail(request, email);

    ModelAndView mav = new ModelAndView("passwordReset/resetPasswordRequestSent");
    mav.addObject("email", email);
    return mav;
  }

  /** Submits a token to access the password change dialog */
  @GetMapping("/verificationPasswordResetReply")
  public ModelAndView getPasswordResetPage(@RequestParam("token") String token) {
    return passwordResetEmailHandler
        .getResetPage(token)
        .addObject("passwordType", PasswordType.VERIFICATION_PASSWORD);
  }

  @PostMapping("/verificationPasswordResetReply")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"pwd", "confirmPwd"})
  public ModelAndView submitPasswordResetPage(
      @ModelAttribute PasswordResetCommand cmd, BindingResult errors, HttpServletRequest request) {
    return passwordResetEmailHandler
        .submitResetPage(cmd, errors, request)
        .addObject("passwordType", PasswordType.VERIFICATION_PASSWORD);
  }
}
