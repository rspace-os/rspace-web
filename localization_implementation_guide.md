# RSpace Localization Implementation Guide

This guide explains how to enable translations and allow users to choose their preferred language in the RSpace application.

## Current State Analysis

The RSpace application already has a robust localization infrastructure in place:

1. **Message Source Configuration**: Uses `ReloadableResourceBundleMessageSource` in `applicationContext-resources.xml`
2. **Resource Bundle Organization**: Messages are organized into logical modules (inventory, workspace, gallery, etc.)
3. **Existing Localization**: Many messages are already properly localized using `<spring:message code="..." />` tags
4. **Infrastructure Ready**: The Spring configuration is already set up to support localization

However, there are still hardcoded strings in JSP files that need to be localized.

## 1. Add Translated Resource Bundle Files

Create translated versions of your resource bundles by adding language-specific files alongside the base properties files. The existing structure:

```
src/main/resources/bundles/
├── admin/
│   ├── admin.properties
│   └── community.properties
├── apps/
│   └── apps.properties
├── dashboard/
│   └── dashboard.properties
├── gallery/
│   ├── gallery.properties
│   └── netfiles.properties
├── groups/
│   └── groups.properties
├── inventory/
│   └── inventory.properties  ← Add translations here
├── public/
│   └── public.properties
├── system/
│   ├── community.properties
│   └── system.properties
└── workspace/
    ├── editor.properties
    └── workspace.properties
```

To add Spanish translations, create files like:
- `src/main/resources/bundles/inventory/inventory_es.properties`
- `src/main/resources/bundles/workspace/workspace_es.properties`

Each file contains the same keys but with translated values:
- `inventory.properties`: `errors.inventory.expiryDate.invalid=Expiry date {0} must be in the future.`
- `inventory_es.properties`: `errors.inventory.expiryDate.invalid=La fecha de vencimiento {0} debe ser en el futuro.`

## 2. Identify Hardcoded Strings Needing Localization

Based on my analysis, I found the following hardcoded strings in JSP files that need to be localized:

### In `/WEB-INF/pages/workspace/editor/include/editdeleteButton.jsp`:
```html
<button class="editButton btn btn-default" onclick="editField(${field.id})">Edit</button>
<button class="deleteButton btn btn-default" onclick="deleteField(${field.id})">Delete</button>
```

### In `/WEB-INF/pages/workspace/editor/include/messagingDialogs.jsp`:
```html
<h3>Send a message to {{label}}</h3>
<label class="extMessageRequestLabel" for="extMessageChannelsSelect">Channel</label>
<label class="extMessageRequestLabel" for="extMessageRequestMessage">Message</label>
<textarea class="extMessageRequestMessage" placeholder="Check my document!"/></textarea>
<i>The message will include a link to the current document. Only RSpace users with access 
   to the document will be able to use the link.</i>
<i>The message will include links to selected documents. Only RSpace users with access 
   to these documents will be able to use the links.</i>
```

### In `/WEB-INF/pages/workspace/editor/include/templatePublishShareDlg.jsp`:
```html
<p> Configure access to your form by groups that you belong to, or by all RSpace users.
   If you don't choose any options, the form will remain private to you.</p>
```

## 3. Replace Hardcoded Strings with Localized Messages

For each hardcoded string found, follow these steps:

### Step 1: Add Messages to Resource Bundle
Add entries to the appropriate `.properties` file (e.g., `workspace/editor.properties`):

```properties
# Editor button labels
button.edit=Edit
button.delete=Delete

# Messaging dialog texts
messaging.dialog.title=Send a message to {0}
messaging.dialog.channel.label=Channel
messaging.dialog.message.label=Message
messaging.dialog.message.placeholder=Check my document!
messaging.dialog.single.doc.info=The message will include a link to the current document. Only RSpace users with access to the document will be able to use the link.
messaging.dialog.multiple.docs.info=The message will include links to selected documents. Only RSpace users with access to these documents will be able to use the links.

# Template publish/share dialog
template.publish.share.info=Configure access to your form by groups that you belong to, or by all RSpace users. If you don't choose any options, the form will remain private to you.
```

### Step 2: Replace Hardcoded Strings in JSP Files
Replace the hardcoded strings with Spring message tags:

#### Before:
```html
<button class="editButton btn btn-default" onclick="editField(${field.id})">Edit</button>
```

#### After:
```html
<button class="editButton btn btn-default" onclick="editField(${field.id})">
    <spring:message code="button.edit"/>
</button>
```

#### Before:
```html
<h3>Send a message to {{label}}</h3>
```

#### After:
```html
<h3><spring:message code="messaging.dialog.title" arguments="{{label}}"/></h3>
```

## 4. Configure Locale Resolution

The application needs to determine which locale to use for each user. Spring provides several ways to do this:

### Option A: Session-based Locale Resolution (Recommended)
Add this to your `WebConfig.java` or equivalent configuration class:

```java
@Bean
public LocaleResolver localeResolver() {
    SessionLocaleResolver slr = new SessionLocaleResolver();
    slr.setDefaultLocale(Locale.ENGLISH);
    return slr;
}
```

### Option B: Cookie-based Locale Resolution
```java
@Bean
public LocaleResolver localeResolver() {
    CookieLocaleResolver clr = new CookieLocaleResolver();
    clr.setDefaultLocale(Locale.ENGLISH);
    clr.setCookieName("rspace_locale");
    clr.setCookieMaxAge(365 * 24 * 60 * 60); // 1 year
    return clr;
}
```

## 5. Add Locale Change Interceptor

Enable users to switch languages by adding a locale change interceptor:

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    LocaleChangeInterceptor localeInterceptor = new LocaleChangeInterceptor();
    localeInterceptor.setParamName("lang"); // URL parameter to change locale
    registry.addInterceptor(localeInterceptor);
    
    // ... other interceptors
}
```

This allows users to change their language by visiting URLs like:
- `?lang=en` for English
- `?lang=es` for Spanish
- `?lang=fr` for French

## 6. Add UI Language Selector

Create a UI component that allows users to select their preferred language. This could be:

### In your header navigation:
```html
<div class="language-selector">
    <select onchange="changeLanguage(this.value)">
        <option value="en" th:selected="${#locale.language == 'en'}">English</option>
        <option value="es" th:selected="${#locale.language == 'es'}">Español</option>
        <option value="fr" th:selected="${#locale.language == 'fr'}">Français</option>
        <option value="de" th:selected="${#locale.language == 'de'}">Deutsch</option>
    </select>
</div>

<script>
function changeLanguage(lang) {
    window.location.href = '?lang=' + lang;
}
</script>
```

### Or as flags/buttons:
```html
<div class="language-flags">
    <a href="?lang=en" th:classappend="${#locale.language == 'en'} ? 'active'"><img src="/images/flags/en.png" alt="English"></a>
    <a href="?lang=es" th:classappend="${#locale.language == 'es'} ? 'active'"><img src="/images/flags/es.png" alt="Español"></a>
    <a href="?lang=fr" th:classappend="${#locale.language == 'fr'} ? 'active'"><img src="/images/flags/fr.png" alt="Français"></a>
    <a href="?lang=de" th:classappend="${#locale.language == 'de'} ? 'active'"><img src="/images/flags/de.png" alt="Deutsch"></a>
</div>
```

## 7. Store User Language Preference (Optional)

To persist user language preferences, you can store them in the user profile:

### Add to User model:
```java
@Column(name = "preferred_language")
private String preferredLanguage;
```

### In a controller or service:
```java
@RequestMapping("/setLanguage")
@ResponseBody
public String setLanguage(@RequestParam String lang, Principal principal) {
    // Save user preference to database
    userService.setUserLanguage(principal.getName(), lang);
    
    // Change current session locale
    Locale locale = new Locale(lang);
    LocaleContextHolder.setLocale(locale);
    
    return "Language set to: " + lang;
}
```

## 8. Pre-select User's Preferred Language

When a user logs in, automatically set their preferred locale:

```java
@Component
public class UserLocaleInitializer {
    
    @EventListener
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getPrincipal().toString();
        User user = userService.findByUsername(username);
        
        if (user.getPreferredLanguage() != null) {
            Locale locale = new Locale(user.getPreferredLanguage());
            LocaleContextHolder.setLocale(locale);
        }
    }
}
```

## 9. Testing the Implementation

To test that your localization works:

1. Create sample translation files with obviously different translations
2. Restart your application
3. Visit your application with different `?lang=` parameters
4. Verify that the messages change appropriately

Example test files:
- `inventory_es.properties`: `errors.inventory.expiryDate.invalid=¡La fecha de vencimiento {0} debe ser en el futuro!`
- `inventory_fr.properties`: `errors.inventory.expiryDate.invalid=La date d'expiration {0} doit être dans le futur !`

## Summary

The key steps are:
1. Create translated `.properties` files with language codes (e.g., `_es`, `_fr`)
2. Identify and replace hardcoded strings in JSP files with `<spring:message>` tags
3. Add corresponding entries to the resource bundle files
4. Configure a `LocaleResolver` bean to determine which locale to use
5. Add a `LocaleChangeInterceptor` to allow switching languages via URL parameter
6. Add UI elements to let users select their preferred language
7. Optionally store and load user language preferences from the database

The existing infrastructure in RSpace is already well-prepared to support this - you just need to add the translated files and update the JSP files with proper localization tags.