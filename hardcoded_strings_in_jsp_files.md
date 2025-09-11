# Hardcoded Strings in JSP Files

This document lists all the hardcoded strings found in JSP files that should be moved to resource bundles for localization support.

## Public Pages

### `/src/main/webapp/WEB-INF/pages/public/requestPasswordReset.jsp`

1. **Hardcoded title**:
   ```html
   <title><spring:message code="public.requestPassword.title"/></title>
   ```
   *Note: This one is already properly localized*

2. **Hardcoded heading text**:
   ```html
   <h2 class="form-signup-heading">Forgotten your password?</h2>
   ```
   Should be: `<h2 class="form-signup-heading"><spring:message code="public.requestPassword.forgotPassword"/></h2>`

3. **Hardcoded placeholder text**:
   ```html
   placeholder="Your Email Address"
   ```
   Should be: `placeholder="<spring:message code="public.requestPassword.email.placeholder"/>"`

4. **Hardcoded title attribute**:
   ```html
   title="Use a valid email address"
   ```
   Should be: `title="<spring:message code="public.requestPassword.email.title"/>"`

5. **Hardcoded button text**:
   ```html
   <span class="ui-button-text">Submit</span>
   ```
   Should be: `<span class="ui-button-text"><spring:message code="button.submit"/></span>`

### `/src/main/webapp/WEB-INF/pages/public/signupConfirmation.jsp`

1. **Hardcoded title**:
   ```html
   <title>Sign up request confirmed</title>
   ```
   Should be: `<title><spring:message code="public.signupConfirmation.title"/></title>`

2. **Hardcoded heading text**:
   ```html
   <h2 class="form-signup-heading">Sign Up Requested</h2>
   ```
   Should be: `<h2 class="form-signup-heading"><spring:message code="public.signupConfirmation.heading"/></h2>`

3. **Hardcoded paragraph text**:
   ```html
   Thank you for signing up to RSpace. <br/>
   You will shortly receive an email confirming that your account has been set up.
   ```
   Should be: `<spring:message code="public.signupConfirmation.thankYou"/><br/>
   <spring:message code="public.signupConfirmation.emailConfirmation"/>`

### `/src/main/webapp/WEB-INF/pages/public/accountDisabled.jsp`

1. **Hardcoded heading text**:
   ```html
   <h2 class="form-signup-heading">Login not Available</h2>
   ```
   Should be: `<h2 class="form-signup-heading"><spring:message code="account.disabled.loginNotAvailable"/></h2>`

### `/src/main/webapp/WEB-INF/pages/public/requestUsernameReminder.jsp`

1. **Hardcoded title**:
   ```html
   <title>Request username reminder</title>
   ```
   Should be: `<title><spring:message code="public.requestUsername.title"/></title>`

2. **Hardcoded heading text**:
   ```html
   <h2 class="form-signup-heading">Forgotten your username?</h2>
   ```
   Should be: `<h2 class="form-signup-heading"><spring:message code="public.requestUsername.forgotUsername"/></h2>`

3. **Hardcoded paragraph text**:
   ```html
   <p>Please enter the email address that you registered with RSpace -
       your username will be sent to that address.</p>
   ```
   Should be: `<p><spring:message code="public.requestUsername.instructions"/></p>`

4. **Hardcoded placeholder text**:
   ```html
   placeholder="Your Email Address"
   ```
   Should be: `placeholder="<spring:message code="public.requestUsername.email.placeholder"/>"`

5. **Hardcoded title attribute**:
   ```html
   title="Use a valid email address"
   ```
   Should be: `title="<spring:message code="public.requestUsername.email.title"/>"`

6. **Hardcoded button text**:
   ```html
   <span class="ui-button-text">Submit</span>
   ```
   Should be: `<span class="ui-button-text"><spring:message code="button.submit"/></span>`

## Solution

To fix these hardcoded strings, we need to:

1. **Add the missing message codes to the appropriate resource bundle files**:
   - `/src/main/resources/bundles/public/public.properties`

2. **Replace the hardcoded strings with Spring message tags**

### Example additions to `public.properties`:

```properties
# requestPasswordReset.jsp
public.requestPassword.forgotPassword=Forgotten your password?
public.requestPassword.email.placeholder=Your Email Address
public.requestPassword.email.title=Use a valid email address

# signupConfirmation.jsp
public.signupConfirmation.title=Sign up request confirmed
public.signupConfirmation.heading=Sign Up Requested
public.signupConfirmation.thankYou=Thank you for signing up to RSpace.
public.signupConfirmation.emailConfirmation=You will shortly receive an email confirming that your account has been set up.

# accountDisabled.jsp
account.disabled.loginNotAvailable=Login not Available

# requestUsernameReminder.jsp
public.requestUsername.title=Request username reminder
public.requestUsername.forgotUsername=Forgotten your username?
public.requestUsername.instructions=Please enter the email address that you registered with RSpace - your username will be sent to that address.
public.requestUsername.email.placeholder=Your Email Address
public.requestUsername.email.title=Use a valid email address
```

### Updated JSP files:

#### `/src/main/webapp/WEB-INF/pages/public/requestPasswordReset.jsp`
```html
<%@ include file="/common/taglibs.jsp"%>

<head>
    <title><spring:message code="public.requestPassword.title"/></title>
    <link href="/styles/pages/public/passwordReset.css" rel="stylesheet">
</head>

<div class="container passwordResetContainer">
    <div class="row">
        <axt:biggerLogo/>
        <div class="passwordResetInstructionDiv">
            <h2 class="form-signup-heading"><spring:message code="public.requestPassword.forgotPassword"/></h2>
            <p><spring:message code="public.requestPassword.help1"/></p>
        </div>
    </div>
    <form class="form-signup" method="POST" action="/signup/passwordResetRequest">
      <fieldset>
          <input type="text" name="email" id="email" class="form-control" 
                 placeholder="<spring:message code="public.requestPassword.email.placeholder"/>" 
                 required="true" autofocus="true" pattern="[^ @]*@[^ @]*" 
                 title="<spring:message code="public.requestPassword.email.title"/>">
          <button class="btn btn-lg btn-primary btn-block" type="submit" 
                  role="button" aria-disabled="false" name="submit" value="Reset">
              <span class="ui-button-text"><spring:message code="button.submit"/></span>
          </button>
      </fieldset>
    </form>
</div>
```

Similar updates would be applied to the other JSP files identified above.