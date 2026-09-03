# Validating Java i18n keys

`ServerMessageCatalogueTest` parses production Java with the JDK compiler tree
API and checks statically known message keys against the production JSON
catalogues.

Run the check from the repository root:

```bash
./mvnw test -Dtest=ServerMessageCatalogueTest -Dfast=true
```

The test scans:

- calls through `MessageSourceUtils`, Spring message sources, inherited
  `getText` and `getMessage` helpers, and known project wrappers;
- built-in-content helpers, email subject keys, and Spring `Errors.reject` or
  `rejectValue`;
- message-carrying exceptions and `MessageSourceResolvable` objects;
- explicit Bean Validation messages, custom constraint defaults, and default
  Jakarta or Hibernate constraint messages.

Literal keys, final constants, constant concatenations, and finite conditional
expressions are resolved. An expression that cannot be reduced to a finite set
of keys fails the test. Keep the complete key visible at the lookup boundary:

```java
messages.getMessage(
    enabled ? "notifications.enabled" : "notifications.disabled");
```

Do not compose keys from runtime data:

```java
messages.getMessage("notifications." + status); // rejected
```

If runtime selection is genuinely open-ended, put the finite mapping in one
place and give its producer a focused test that resolves every result.

Bean Validation uses `JsonMessageSource` through the validator bean in
`BaseConfig`. Application-specific and standard constraint messages therefore
belong in `server.*.json`; do not add `ValidationMessages.properties`.

The checker loads catalogue resources only from
`src/main/webapp/ui/src/modules/common/i18n/locales`, so test-only catalogues
cannot satisfy production references. GitHub Actions and Jenkins both treat
locale JSON edits as Java-test changes.
