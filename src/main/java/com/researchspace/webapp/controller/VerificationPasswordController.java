package com.researchspace.webapp.controller;

import com.researchspace.model.ProductType;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.service.IVerificationPasswordValidator;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
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
      @RequestParam("confirmVerificationPassword") String confirmVerificationPassword) {

    User user = userManager.getAuthenticatedUserInSession();

    if (verificationPasswordValidator.isVerificationPasswordSet(user)) {
      SECURITY_LOG.warn(
          " {} - [{}] attempted to set verification password, but it has already been set.",
          user.getFullName(),
          user.getId());
      return new AjaxReturnObject<>("Verification password has already been set.", null);
    }

    String newPass = StringUtils.trim(newVerificationPassword);
    String confirmPass = StringUtils.trim(confirmVerificationPassword);

    if (isInputStringBlank(newPass) || isInputStringBlank(confirmPass)) {
      return new AjaxReturnObject<>("Please enter data in all fields", null);
    }

    String checkPasswordResult =
        userValidator.validatePasswords(
            newVerificationPassword, confirmVerificationPassword, user.getUsername());
    if (!UserValidator.FIELD_OK.equals(checkPasswordResult)) {
      SECURITY_LOG.warn(
          "{} [{}] unsuccessfully attempted to set verification password.",
          user.getFullName(),
          user.getId());
      return new AjaxReturnObject<>(checkPasswordResult, null);
    }

    String encryptedPass = verificationPasswordValidator.hashVerificationPassword(newPass);

    user.setVerificationPassword(encryptedPass);
    userManager.saveUser(user);

    SECURITY_LOG.info(
        "{} [{}] successfully set verification password", user.getFullName(), user.getId());
    return new AjaxReturnObject<>("Verification password set successfully", null);
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
      @ModelAttribute PasswordResetCommand cmd, BindingResult errors) throws Exception {
    return passwordResetEmailHandler
        .submitResetPage(cmd, errors)
        .addObject("passwordType", PasswordType.VERIFICATION_PASSWORD);
  }
}
