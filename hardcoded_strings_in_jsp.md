# Hardcoded Strings in JSP Files

## Found Instances

### `/src/main/webapp/WEB-INF/pages/public/requestPasswordReset.jsp`

1. **Hardcoded heading text**:
   ```html
   <h2 class="form-signup-heading">Forgotten your password?</h2>
   ```
   Should be replaced with:
   ```html
   <h2 class="form-signup-heading"><spring:message code="public.requestPassword.forgotPassword"/></h2>
   ```

2. **Hardcoded placeholder text**:
   ```html
   <input type="text" name="email" id="email" class="form-control" placeholder="Your Email Address" 
           required="true" autofocus="true" pattern="[^ @]*@[^ @]*" title="Use a valid email address">
   ```
   Should be replaced with:
   ```html
   <input type="text" name="email" id="email" class="form-control" placeholder="<spring:message code="public.requestPassword.email.placeholder"/>" 
           required="true" autofocus="true" pattern="[^ @]*@[^ @]*" title="<spring:message code="public.requestPassword.email.title"/>">
   ```

3. **Hardcoded button text**:
   ```html
   <span class="ui-button-text">Submit</span>
   ```
   Should be replaced with:
   ```html
   <span class="ui-button-text"><spring:message code="button.submit"/></span>
   ```

## Solution

To fix these hardcoded strings, we need to:

1. Add the missing message codes to the appropriate resource bundle file:
   - `/src/main/resources/bundles/public/public.properties`

2. Replace the hardcoded strings with Spring message tags:
   ```properties
   # Add to public.properties
   public.requestPassword.forgotPassword=Forgotten your password?
   public.requestPassword.email.placeholder=Your Email Address
   public.requestPassword.email.title=Use a valid email address
   ```

3. Update the JSP file to use the message tags instead of hardcoded strings.