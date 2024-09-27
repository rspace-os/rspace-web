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
