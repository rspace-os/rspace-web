# Validation 

This document describes various ways to validate objects in RSpace, especially input
coming into controller methods.

## Javax.validator and Hibernate Validator

We can annotate objects in Controller methods with `@Valid` and include a
`BindingResult` in the argument list, Spring will invoke Javax validator,
find `HibernateValidator` in classpath and populate a `BindingResult` with errors
mapped to fields, performing message interpolation of any error messages in
`ValidationMessage.properties`.

We can also annotate model `@Entity` classes. They'll be validated when saving to
database (`RealTransaction` tests can cover that validation too).
