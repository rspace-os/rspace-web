# Glossary

This file is just a glossary of terms that is used across the code base, both
in the naming of code constructs and in the comments surrounding them.

## A

### -Args

Any type ending in "Args" describes the shape of the props expected by a react component.

### A11y

See [accessibility](#accessibility).

### Accessibility

> Accessibility (often abbreviated to A11y — as in, "a", then 11 characters,
> and then "y") in web development means enabling as many people as possible to
> use websites, even when those people's abilities are limited in some way. —
> [MDN](https://developer.mozilla.org/en-US/docs/Web/Accessibility)

### Action

[Mobx actions](https://mobx.js.org/actions.html) are functions or methods that
update state that is being managed by mobx. When the state is updated, any
computed properties that are being observed are re-computed and any observing
react components are re-rendered.

### Aliquot

An often used term for a fraction of a sample; a common alias of subsample.

### Alt-text

A string that is associated with an image so that blind users using screen
readers can have the contents of the image described to them. For more
information, [see the MDN documentation on the `alt` attribute of
HTMLImageElements](https://developer.mozilla.org/en-US/docs/Web/API/HTMLImageElement/alt).

### Arbitrary

An arbitrary is a data structure used with the [fast-check testing
library](https://fast-check.dev/) that encapsulates the generation of random
values. It is paramaterised by a type that describes
the shape of the values that will be generated when the test is executed, for
example `fc.string()` generates random strings and has the type
`Arbitrary<string>`. These arbitraries can be composed and combined to generate
all manner of possible JavaScript objects. For more information, [see the
fast-check documentation on
Arbitraries](https://fast-check.dev/docs/introduction/getting-started/#arbitrary)

### aria-

Aria-* attributes are HTML attributes for adding additional information for 
accessibility technologies like screen readers. [For more information on ARIA, see
the MDN documentation.](https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA)

### Aspose

Aspose is a third-party service for converting between common document formats.
We mostly use it to generate PDFs of word, excel, and other office documents.
[For more information, see Aspose's website.](https://products.aspose.app/)

### axe

Axe is a collection of testing tools for validating [accessibility](#accessibility) compliance.
There is a core library and variety of tools built on top including browser
extensions and automated testing packages.
[For more information, see axe's website](https://www.deque.com/axe/)

### axios

[Axios](https://axios-http.com/docs/intro) is a promise-based HTTP client library.
Whilst the [Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API)
is widely available we continue to use axios as it easily mocked with
[axios-mock-adapter](https://www.npmjs.com/package/axios-mock-adapter), abstracts 
away the JSON serialisation/deserialisation, has built-in support for timeouts 
without having to use AbortControllers, and exposes upload/download progress for 
enhancing the user experience.

## B

### Barcodes

Barcodes, including QR codes, are used extensively in the labatory setting to
associate digitally recorded information with physical items, including test tubes
and frozen samples. Printing and scanning labels is a common part of the workflow
of our customers, and as such Inventory provides some capabilities to work
cohesively with these workflows.

### Basket

Heterogenous collections of Inventory records that are persisted across sessions,
facilitating bulk operations.

### Bench

A per-user location for subsamples and containers. Designed to be analogous to the
user's workspace in the labatory.

### Breakpoint

Key functionality of a debugging tool that stops the execution of the code at a
predefined point.

A defined width of the browser's viewport, typically measured in pixels, with
respect to which styles that responsively adapt user interface are defined.
These breakpoints are typically chosen to categorise viewport sizes into
phones, tablets, and common window sizes on desktops and laptops but this is
rather arbitrary as there are phones larger than the smallest tablets and 
windows can be resized to various dimensions. Most UI code is abstracted away
from the particular device and is simply concerned with whichever is the 
largest breakpoint smaller than the viewport's current width.

## C

### Card

A card is a region of the user interface that contains semantically related elements,
defined with either a border or drop shadow. Popularised by [Google's Material design](https://m3.material.io/)
([see Cards as they were originally designed in version 1 of Material design](https://m1.material.io/components/cards.html)),
they have gone on to become a key part of the design language of modern Web development.

### clsx

[clsx](https://www.npmjs.com/package/clsx) is a utility library for constructing
HTML class attributes with conditional logic.

### Code point

A numerical value in the Unicode codespace that may or may not have an assigned
meaning. Multiple code points form [grapheme clusters](#grapheme-cluster).
[For more information, see the Code Point entry of the Unicode glossary](https://www.unicode.org/glossary/#code_point).

### Colour contrast

The degree to which adjacent elements are displayed using colours that are
sufficiently different so as to be easily distinguished. This is a critical
aspect of [accessible design](#accessibility), and is discussed at length in
[Accessibility.md](./Accessibility.md).

### Component Tree

The tree data structure created by react components calling other react components.
From the perspective of our code, mostly MUI components, with only some HTML elements,
are the leaf nodes. The component tree can be interactively explored with the
[react developer tools](https://react.dev/learn/react-developer-tools).

### Computed Property

Computed properties are the properties of JavaScript object or instances of classes
whose value is automatically derived by mobx from the object's observable state.
They are implemented using [getters](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Functions/get),
and are typed using Flow as [read-only properties](https://flow.org/en/docs/lang/variance/#toc-covariance).
For [more information on mobx computeds, see their documentation](https://mobx.js.org/computeds.html).

### Container

An Inventory record that models all manner of physical containers in the labatory,
from freezers to well-plates. It can also be used to model logical groupings of
items that exist in the same physical location, or items that have not yet been
placed in such a location.

### Container Queries

A CSS feature for selecting elements based on their surrounding container.

> Container queries enable you to apply styles to an element based on the
> size of the element's container. If, for example, a container has less
> space available in the surrounding context, you can hide certain elements
> or use smaller fonts. — [MDN](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_containment/Container_queries)

### Context

A react context defines a region of the [component tree](#component-tree)
within which a particular set of values are globally available. They are an
incredibly powerful feature that goes beyond just avoiding prop drilling:
facilitating dynamic dispatching based on where a component is rendered
in component tree, providing seams that can be mocked in a test environment,
and exposing global functions that abstract reusable page-wide functionality
like alerting, navigation, and modal dialogs. For more information, see
[the react docs](https://react.dev/learn/passing-data-deeply-with-context).

### Custom field

In addition to the standard fields that we provide, like description, tags,
and expiry date, samples can have additional fields of various types. These
are defined on a sample template and are inherited by any samples created by
that template. There is no way other way to additional custom fields, although
[extra fields](#extra-field) can be added to samples on an ad-hoc basis.

### Custom hook

A function that is executed from within a react component or another custom
hook and has access to all of the react runtime's environment. By convention,
they always start with "use". They are very useful for separating view logic
from models and API calls, as well as for creating application-specific
abstractions over applications of the generic low-level react APIs like
useEffect and [contexts](#context). For more information, see
[the react docs](https://react.dev/learn/reusing-logic-with-custom-hooks).

## D

### Data Management Plan

A [Data Management Plan](https://en.wikipedia.org/wiki/Data_management_plan)
(abbreviated DMP) is a document prepared prior to beginning research for
describing how the data will be handled, any relevant metadata, and how the
data will be prepared for archiving and preservation.

### DataGrid

[DataGrid](https://mui.com/x/react-data-grid/) is a complex react component
provided by MUI for displaying tabular data.

### Dependency Cruiser

[Dependency Cruiser](https://github.com/sverweij/dependency-cruiser) is an
open-source tool for performing static analysis on a JavaScript project to
validate and report on the dependencies between modules.

### Dialog

> A dialog is a window overlaid on either the primary window or another dialog
> window. Windows under a modal dialog are inert. That is, users cannot
> interact with content outside an active dialog window. Inert content outside
> an active dialog is typically visually obscured or dimmed so it is difficult
> to discern, and in some implementations, attempts to interact with the inert
> content cause the dialog to close. — [ARIA APG](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/)

### Disjoint object union

A Flow type where each value of the type is an object and there is a property
that has a distinct constant value for each branch of the union, thereby
supporting refinement. It is the closest thing that JavaScript with Flow type
annotations has to the tagged unions of pure functional programming languages.
For more information, see [the Flow docs](https://flow.org/en/docs/types/unions/#toc-disjoint-object-unions).

### Dynamic dispatch

The process of selecting which implementation of some code to execute at runtime.
The canonical example is the nominal subtyping of class hierarchies in
languages like Java, or the prototypical inheritance in JavaScript, however the
principle applies anywhere where executable code is not statically known at
compile time, such as through dependency injection or by passing higher-order
functions. [React contexts](#context) are a way of dynamically dispatching
based on where a component is rendered within the component tree, providing
different implementations of an interface based on which context is within
scope where the component is rendered.

## E

### Electronic Lab Notebooks

Often abbreviated as 'ELN', these are pieces of software designed to replace
the traditional notebooks that scientists and researchers kept in laboratories
to record experimental results. In addition to being central to collaboration
and a reference for future publishing, they also form a key part of the
evidence in enforcing intellectual property rights.

### Eslint

A static analysis tool for identifying potentially problematic patterns in
JavaScript code. Via its plugin system, it can also be used to enforce code
style.

### Extra field

In addition to the standard fields that we provide, like description, tags,
and expiry date, all Inventory records can have additional fields. These
are defined in an ad-hoc manner on a per-record basis with no mechanism for
replicate them across multiple records.

## F

### fast-check

[Fast-check](https://fast-check.dev/) is a property-based testing library for
JavaScript.

### Field

A piece of editable data associated with an Inventory record, such as name,
description, or expiry date. For the additional fields that samples inherit
from templates, see [custom field](#custom-field). For additional fields that
the user can add to any record, see [extra field](#extra-field).

### Flow

[Flow](https://flow.org/) is a static type checker for JavaScript. It's very
similar to TypeScript, and the reason we use Flow is largely historical.

### FlowExpectedError

See [Flow suppression](#flow-supression).

### Flow suppression

> A suppression is a special kind of comment that you can place on the line
> before a type error. It tells Flow not to report that error when checking
> your code. — [Flow](https://flow.org/en/docs/errors/)

### Form

> Web forms are one of the main points of interaction between a user and a
> website or application. Forms allow users to enter data, which is generally
> sent to a web server for processing and storage (see Sending form data later
> in the module), or used on the client-side to immediately update the
> interface in some way (for example, add another item to a list, or show or
> hide a UI feature). — [MDN](https://developer.mozilla.org/en-US/docs/Learn/Forms/Your_first_form#what_are_web_forms)

In terms of the RSpace ELN, a form is a template for a document. The user can
define ahead of a time a format to record their experimental data and easily
create new documents of the same structure, ensuring that experimental results
are recorded consistently.

## G

### Gallery

The Gallery is a section of the ELN for storing and organising files and other
ancillary data, organised by category of file type. For more information, see
[our user documentation on the Gallery](https://documentation.researchspace.com/article/sl6mo1i9do-the-gallery).

### Geolocation

A geolocation is the digital description of the real-world location of an
object. It is often used in the context of IGSNs, in which case for more
information see the [DataCite documentation on GeoLocation](https://datacite-metadata-schema.readthedocs.io/en/4.5/properties/geolocation/)

### Global Id

A string that uniquely describes a record on a single instance of RSpace. They
are of the form `[A-Z][A-Z][0-9]+`, where the two-character prefix denotes the
record type. Some Global Ids are exposed in the user interface, where they form
the basis of permalinks and may be referenced across the product, but many more
are hidden from view as they offer no such benefit and would only serve to
increase the visual noise.

### Grapheme Cluster

One or more Unicode [code points](#code-point) that form a ["horizontally
segmented unit of text"](https://www.unicode.org/glossary/#grapheme_cluster),
i.e. what we would colloquially call a character.

### Grid Container

A type of [container](#container) that requires that all of its contents be
arranged in the two-dimensional grid of a predefined size. This is typically
used for modelling well-plates.

## I

### Identifier

See persistent identifier

### IGSN

The International Generic Sample Number, or IGSN, is the standard persistent
identifier for samples.
