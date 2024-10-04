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

### Conjuction

The monoidal operator that reduces a set of booleans down to a single
boolean, only returning a truthful state when all of the inputs are true.

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

### Cypress

A JavaScript testing framework and test runner, for end-to-end tests.
See [the Cypress website](https://www.cypress.io/) for more information.

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

### Disjuction

The monoidal operator that reduces a set of booleans down to a single
boolean, returning a truthful state when any of the inputs are true.

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

### Endpoint

The location of a resource of a Web [API](#application-programming-interface).
Typically expressed as a URL, accepts search parameters and/or a body, and
returns a response with a status code and a body.

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

[Fast-check](https://fast-check.dev/) is a
[property-based testing](#property-based-testing) library for JavaScript.

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

1. A form in the generic sense,
> Web forms are one of the main points of interaction between a user and a
> website or application. Forms allow users to enter data, which is generally
> sent to a web server for processing and storage (see Sending form data later
> in the module), or used on the client-side to immediately update the
> interface in some way (for example, add another item to a list, or show or
> hide a UI feature). — [MDN](https://developer.mozilla.org/en-US/docs/Learn/Forms/Your_first_form#what_are_web_forms)

2. In terms of the RSpace ELN, a form is a template for a document. The user
   can define ahead of a time a format to record their experimental data and
   easily create new documents of the same structure, ensuring that
   experimental results are recorded consistently.

### Functional Reactive Programming

Functional reactive programming is a hybrid programming paradigm where
declarative constructs are used to declare the data flows, using the constructs
of functional programming to tie it all together.

## G

### Gallery

The Gallery is a section of the ELN for storing and organising files and other
ancillary data, organised by category of file type. For more information, see
[our user documentation on the Gallery](https://documentation.researchspace.com/article/sl6mo1i9do-the-gallery).

### Geolocation

A geolocation is the digital description of the real-world location of an
object. It is often used in the context of [IGSNs](#igsn), in which case for
more information see the
[DataCite documentation on GeoLocation](https://datacite-metadata-schema.readthedocs.io/en/4.5/properties/geolocation/)

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

## H

### Handle

An abstract reference to a resource; often an identifier or a pointer in a
table to which the code that holds the handle has no access, but may equally be
any other value that the code is prevented from inspecting. Common examples
include file descriptors, network sockets, and process identifiers.

### Higher Order Component

Also known as a HoC, these are functions that takes as argument a React
component and return a new React component. Given the fact that 
[the only official documentation on HoCs](https://legacy.reactjs.org/docs/higher-order-components.html)
is from the legacy docs, one can only assume that they are a deprecated feature
that should be avoided.

## I

### Idempotence

The property of a function that invoking it twice has no effect more than
calling it once; naturally this means that it must be [pure](#pure).

### Identifier

See persistent identifier

### IGSN

The International Generic Sample Number, or IGSN, is the standard
[persistent identifier](#persistent-identifier) for samples.

### Intercom

A third-party service that provides a messaging widget for communicating
directly to support from within a web application.
See [their website](https://www.intercom.com/) and their
[developer documentation](https://developers.intercom.com/docs) for more information.

### Interface

1. That which a given module of code exposes to the rest of the system, which is
   to say
> [An] interface consists of everything that a developer working in a different
> module must know in order to use the given module.
>   — A Philosophy of Software Design.

2. An [interface in Flow](https://flow.org/en/docs/types/interfaces/) is the same
   as Java: an abstract definition of a class that declares what the classes
   that implement it expose to the rest of the system. There is no way to have
   an object that is not an instance of a class to implement an interface,
   there an object type alias must be used instead.

3. For the interface exposed to user, see [User Interface](#user-interface).

4. For the interface that the server exposes to the clients, like Web browser,
   see [API](#application-programming-interface).

### Integration

A core part of the value-add of the RSpace product is the number of
integrations with various third-party services, thereby allowing researchers to
utilise the other software tools that they are using in a collaborative
fashion. In the UI and user-facing documentation, these are often described as
"apps".

### Invariant

> An invariant is a logical assertion that is always held to be true during a
> certain phase of execution of a computer program.
>   — [Wikipedia](https://en.wikipedia.org/wiki/Invariant_(mathematics)#Invariants_in_computer_science)

### iRODS

A third-party service that provides virtualised file storage, offering a single
point of entry for data stored in various locations. For more information, see
[their website](https://irods.org/).

## J

### Jest

A JavaScript testing framework and test runner, for unit tests.
See [the Jest website](https://jestjs.io/) for more information.

## K

### Key-value pair

A piece of data with a name, a collection of which form an associative array
like a JavaScript object.

## L

### Lighthouse

1. A Google Chrome tool for auditing Web pages for performance,
   [accessibility](#acessibility), and other metrics.
   See [the documentation for Lighthouse](https://developer.chrome.com/docs/lighthouse/)
   for more information.

2. A UI widget provided by HelpDocs that allows users to browse documentation
   from within a Web application. For their documentation, see
   [the lighthouse section of the HelpDocs website](https://www.helpdocs.io/lighthouse).

### List Container

A type of [container](#container) that enforces no constraint on how the
contents are organised. Whilst the name would suggest there is an order, the
user has no control over this and thus the container is more analogous to a
set than an array — the name deriving from how the contents are displayed in
the UI.

### List of Materials

A collection of Inventory records attached to the field of an ELN document for
recording which samples, and their quantities, that were used as part of an
experiment.

## M

### Material UI

An open-source component library, loosely based on [Google's Material Design](https://m3.material.io/),
maintained by [MUI](#mui). See [their documentation](https://mui.com/material-ui/getting-started/)
for more information.

### Media query

A CSS feature that allow styles to be conditionally applied based on the user's
device: viewport dimensions, orientation, and preferences. See the [MDN
docs](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_media_queries/Using_media_queries)
for more information.

### Mobx

A JavaScript library that applies the principle of functional reactive
programming to state management. Their docs can be found at
[mobx.js.org](https://mobx.js.org/).  Whilst Mobx is not a React-specific tool,
the [mobx-react-lite](https://www.npmjs.com/package/mobx-react-lite) glue
library effectively makes Mobx an extension of the declarative React system
wherein modifications to the props or state held by contexts automatically
triggers a re-render; with Mobx updates to deeply nested values in objects also
triggering re-rendering.

### Modal

A modal window disables user interaction with the main window but keeps it
visible.

### Model

A domain-specific data structure for co-locating related information e.g. a
person class that has properties for name and age.

### Module

1. In the generic sense, a module is a unit of code that is relatively
   independent of other modules. It has an implementation which hides from the
   rest of the codebase and an interface that it exposes.

2. JavaScript modules, specifically, have a long and complicated history. This
   project uses Webpack so we use [ECMAScript modules](https://webpack.js.org/guides/ecma-script-modules/)
   using [ES2015 import statements](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/import)
   and export to expose only some code constructs to the rest of the codebase.

### MUI

The team that develops and maintains [Material UI](#material-ui), amongst other
projects.

## O

### Observable

A piece of state — the properties on objects, the contents of arrays and sets,
and much else — that is being managed by Mobx and when modified results in the
propagation of those values to the [computed properties](#computed-property)
that are derived from them and the [observing react components](#observer)
re-rendering.

### Observer

A React component that has had the `observer` [HoC](#higher-order-component)
from [Mobx](#mobx) applied such that modifications to the state managed by Mobx
result in the react component being re-rendered. For more information, see
[the Mobx documentation](https://mobx.js.org/react-integration.html).

### Opaque type alias

This is a type whose name is exported to the rest of the codebase but whose
definition is kept hidden. This means that [modules](#module) other than the
one in which it is defined can only treat the value as a [handle](#handle)
whose value cannot be inspected; values can only be created by the defining
module and then may only be operated on by the same module. This sounds
limiting but is a powerful feature in enforcing constraints on a generic type:
by exporting a smart constructor that performs validation logic we ensure that
only valid values are allowed; using this we can define types such as a
numerical that requires that the value not be negative or an array that is not
empty. [Flow's documentation on opaque type aliases](https://flow.org/en/docs/types/opaque-types/)
doesn't provide much help about why and when to use them, but it does explain
the syntax.

## P

### Permalink

A type of [PID](#persitent-identifier), a
> permalink or permanent link is a URL that is intended to remain unchanged
> for many years into the future, yielding a hyperlink that is less susceptible
> to link rot. — [Wikipedia](https://en.wikipedia.org/wiki/Permalink)

### Persistent Identifier

Also called a PID, these are long-lasting identifiers to assets, digital or
otherwise. The key thing to understand is that the identifiers themselves are
only as persistent as the service that resolves them. [Permalinks](#permalink)
and [IGSNs](#igsn) are a form of PIDs.

### Picker

A picker is a UI element that allows the user to select an option from a set of
predefined values, typically presented in a manner that is specific to the type
of data being chosen: e.g. a calendar for dates. Other common examples include
time and files.

### Polymorphism

The ability to present the same interface for different underlying forms and to
operate on such data in an abstract way based only on the information that is
available. To name but three types, sub-typing polymorphism allows code to be
presented with any class that implements a specific interface; ad-hoc
polymorphism allows for particular functions to be defined on several different
types, such as addition over both integers and floats; parametric polymorphism
allows for abstract data structures to be defined that can handle values of any
given type. Polymorphism is perhaps the most important tool is building
scalable, maintainable, and robust software as it is a mechanism for decoupling
functionality and allowing additions to the codebase to continue without being
slowed down by the volume of the existing code.

### Posthog

A third-party tool for collecting and analysing analytics data from across a
fleet of deployed servers. Their website is [posthog.com](posthog.com).

### Predicate

A function that returns a boolean value.

### Prefers Contrast

One of the user preferences that browsers expose to Web pages is whether the
user has enabled a prefers contrast mode in their device settings. This
instructs the Web page to alter the colours to increase the
[contrast](#colour-contrast) between adjacent elements. We take the approach
that it is necessary, and only necessary, to meet the AAA level of compliance
with the colour contrast success criteria of WCAG when this preference is
enabled, although we often do when it is not enabled anyway.

### Prop drilling

The repeated passing of [props](#props) untouched through a chain of react
components so that an component high up in the [component tree](#component-tree)
may pass data to a component many levels below. Whilst somewhat clumsy and
messy, this practice is especially egregious when the components in the middle
should be decoupled from those at top and bottom; in that case, use a
[context](#context).

### Props

The arguments, passed as an object, to a react component.

### Property-based testing

A testing technique where the code under test is not asserted against a known
example, but instead the test runtime generates a set of values that conform to
the inputs of the system and a property is asserted about all of the outputs.
For example, that applying a sort function is the same as apply it twice
(idempotence), or applying a reverse functions twice is the same as never
calling it at all. A great introduction to property based testing is
[this blog series by Scott Wlaschin](https://fsharpforfunandprofit.com/posts/property-based-testing/)

### Pure

A function is pure if its output can be solely determined by inspecting its
inputs; that in principle it could be replaced with a (potentially infinite)
lookup table. Naturally this means it can have no side effects: no network
activity, no logging, no user input, no mutation, no global variables, no
randomness -- just inputs and outputs. This includes mathematical operations
like addition, array operations like map and filter, and most other utility
functions. What makes them so great is that they are really easy to test and
very ameneable to [property-based testing](#property-based-testing).

## R

### Reduced motion

One of the user preferences that browsers expose to Web pages is whether the
user has enabled a reduced motion mode in their device settings. This instructs
the Web page to remove any unnecessary motion, including animations and
transitions. There are very few places in RSpace where motion is a key part of
the functionality so in the vast majority of cases we should be disabling all
motion when this is enabled.

### Repository

1. A directory in a filesystem with a .git directory and whose contents can
therefore be version controlled with git.

2. A third-party service that publishes documents and other datasets for
sharing beyond a particular institution, enabling collaboration across the
scientific community. RSpace integrations with number of repository services,
allowing users to export their work in various different formats.

### Role

> ARIA roles provide semantic meaning to content, allowing screen readers and
> other tools to present and support interaction with an object in a way that
> is consistent with user expectations of that type of object. ARIA roles can
> be used to describe elements that don't natively exist in HTML or exist but
> don't yet have full browser support. — [MDN](https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Roles)

## S

### Sample

A specimen or small quantity of a substance that it is to be used as part of an
experiment. It may be a divisible quantity measured by its weight or volume, or
be constituted of a discrete number of pieces or units.

### Screen reader

A piece of software that audibly describes the visual aspects of another piece
of software. Typically included as part of the operating system, the most
popular amongst the visually impaired community, JAWS, is a separate program.
The tools rely on semantic information about the user interface elements being
exposed to them by the software that they describe and without this information
the user interface is substantially harder to use for those that rely on screen
readers. It should also be noted that screen reader users navigate Web pages
significantly different to sighted users, preferring to navigate by headings,
links, and other landmarks.

### Seam

> a seam is a place where you can alter behavior in your program without
> editing in that place — Michael Feathers; Working Effectively with Legacy
> Systems
For more information, see [this article by Martin Fowler on the topic of seams](https://martinfowler.com/bliki/LegacySeam.html).

### Segment

A third-party tool for collecting and analysing analytics data from across a
fleet of deployed servers. Their website is [segment.com](https://segment.com/).
