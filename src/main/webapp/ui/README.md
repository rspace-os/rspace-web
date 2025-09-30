# RSpace Frontend

This is the modern parts of the frontend application for RSpace, built with
React and TypeScript. The application serves both as a Single Page Application
(SPA) for Inventory, Gallery, and the Apps page, and provides React islands
embedded within the ELN (Electronic Laboratory Notebook) JSP-based pages.

The [webpack configuration](./webpack.config.js) defines multiple entry points
for these different application parts -- see the entry object for the complete
list of bundles and their purposes.

The [package.json](./package.json) defines the dependencies and scripts for
development, testing, and building the application.

The [dependency cruiser config](./dependency-cruiser.js) defines rules for
how the codebase should be organised, ensuring that where code is shared between
different parts of the application it is done so in a controlled manner.

## Architecture

The codebase has evolved over time, and as such contains a mix of architectural
patterns. Many of the older pages are still based on JSPs with jQuery, with much
of their JavaScript code in [../scripts](../scripts), with the exception of the
React islands which can be found in [src/CreateGroup](./src/CreateGroup),
[src/Gallery](./src/Gallery) (this is the old Gallery),
[src/my-rspace](./src/my-rspace), [src/system-groups](./src/system-groups),
[src/system-ror](./src/system-ror), and [src/Toolbar](./src/Toolbar).
[src/tinyMCE](./src/tinyMCE) contains TinyMCE plugins, written in React, that
are used in the ELN for the main rich text editor.

Inventory, the Gallery, the Apps page, and some other newer parts of the ELN are
fully React and TypeScript and also live in this directory. They can be found in
[src/Inventory](./src/Inventory), [src/eln](./src/eln),
[src/eln-dmp-integration](./src/eln-dmp-integration), and
[src/eln-inventory-integration](./src/eln-inventory-integration).

Other important directories include:
- [dist](./dist): The output directory for built files.
- [\_\_tests\_\_](./__tests__): Helper and setup functions for Jest.
- [src/assets](./src/assets): Static assets such as images and branding.
- [src/components](./src/components): Reusable React components used across the
  application.
- [src/hooks](./src/hooks): Custom React hooks for shared logic, in particular
  wrappers for calling our API.
- [src/stores](./src/stores): State management code, including type definitions,
  model classes, React Contexts, and global stores. For more information, see
  the `src/stores/*/README.md` files.
- [src/util](./src/util): Utility functions used across the application.

Some of the older React code still uses legacy patterns such as class components
and MobX stores, while newer code uses functional components with hooks and React
Context for state management. New code should avoid using MobX stores, and
instead use React Context and custom hooks for state management.

## Testing Strategy

### Testing Frameworks

#### Component Tests (Playwright)
Most new React tests (since mid-2025) have been written using Playwright's
component testing capabilities. This allows for more realistic testing of React
components than jest as the tests are run in an actual browser. Whilst slower to
run than Jest unit tests, they provide better confidence that components work as
expected, in particular when it comes to accessibility. The config for Playwright
can be found in the [playwright-ct.config.ts](./playwright-ct.config.ts) file.

Many of the tests have been written using a BDD-style approach, allowing for
the test code to closely align with the feature specification and user stories.
The test code can be found alongside the component code in files with a
`.spec.tsx` suffix. Many of the tests also use a `.story.tsx` file to define
different setups for the componenent as Playwright has restrictions on what
can be done in the test file itself. These story files can also act as a form of
documentation for the component, showing different ways it can be used.

#### Unit Tests (Jest)
Much of the rest of the React codebase is still tested using Jest unit tests. These
tests are generally faster to run than Playwright component tests, but can be
less realistic as they often require more mocking and stubbing of dependencies.
The most compelling reason to avoid using Jest, however, is simply how
frustrating it is to assert properties about a UI that you cannot see. The
config for Jest can be found in the [package.json](./package.json) under the
`jest` key.

The test files can be found in `__tests__` directories alongside the code under test.
This directory usually contains either a file for each file under test in the
parent directory, or else a directory for each file inside of which there will
be a file for each class, module, function, or other construct that can be
tested in isolation.

#### End-to-End Tests (Cypress)
End-to-end tests for the entire RSpace application, including both frontend and
backend, are written using a mixture of Cypress, Selenium, and Playwright and
live in separate repositories.

### Testing Libraries

There are also a few testing libraries that we use.

#### Testing Library
Testing library provides a uniform API for querying the DOM in both Jest and
Playwright tests. It encourages testing the application in a way that is closer
to how a user would interact with it, rather than testing implementation details.

#### Axios Mock Adapter
This library is used to mock Axios requests in Jest tests. It allows us to
define expected requests and responses, making it easier to test components that
make HTTP requests. In playwright, this is handled by the built-in
`router.route` API.

#### Fast Check
Fast Check is a property-based testing library that allows us to define
properties that should hold true for our code, and then generates test cases to
verify those properties. This can help to catch edge cases that may not be
covered by traditional unit tests. It is very handy when testing business logic
in utility functions and model classes, where there are lots of edge cases to
consider. It can be made to work with React components in both Jest and
Playwright, but this is quite difficult to set up and due to performance
constraints usually means that only a few generated values can be asserted for
each property, limiting the usefulness of the approach. It is definitely worth
considering when a component is particularly complex, but if the logic can be
extracted out and tested separately then that is probably wiser than spending
the time to set it up for components.

#### jest-axe
Unlike Playwright which has AxeBuilder built-in for asserting accessiblity, Jest
requires that we import jest-axe separately. They both perform much the same
job, but Playwright is a slightly better tool as it can catch more issues given
that it actually runs in the browser.

### Jenkins
We have four steps in the Jenkins pipeline for running frontend tests:
- Jest Tests (feature branch)
- Jest Tests (main branch)
- Playwright Component Tests (feature branch)
- Playwright Component Tests (main branch)

The main branch steps run all the tests when the build is running against the
`main` branch. The feature branch steps run only the tests that have been
changed since `main`, substantially speeding up the development feedback loop.

## Further Documentation

For detailed information about specific areas

### Directories
- Type definitions: See [src/stores/definitions/README.md](src/stores/definitions/README.md)
- Model classes: See [src/stores/models/README.md](src/stores/models/README.md)
- React Contexts: See [src/stores/contexts/README.md](src/stores/contexts/README.md)
- Global state management: See [src/stores/stores/README.md](src/stores/stores/README.md)
- Custom Hooks: See [src/hooks/README.md](src/hooks/README.md)
- Gallery: See [src/eln/gallery/README.md](src/eln/gallery/README.md)
- Public Pages: See [src/components/PublicPages/README.md](src/components/PublicPages/README.md)
- Inventory Context Menu: See [src/Inventory/components/ContextMenu/README.md](src/Inventory/components/ContextMenu/README.md)

### Topics
- Accessibility: See [../../../../DevDocs/DeveloperNotes/GettingStarted/Accessibility.md](../../../../DevDocs/DeveloperNotes/GettingStarted/Accessibility.md)
- Link Navigation in Inventory: See [src/Inventory/NavigationInInventory.md](src/Inventory/NavigationInInventory.md)
- Adding Form Fields in Inventory: See [src/Inventory/AddingNewFormFields.md](src/Inventory/AddingNewFormFields.md)
- Adding new integrations to the Apps page: See [src/eln/apps/AddingANewIntegration.md](src/eln/apps/AddingANewIntegration.md)
- Odd things to watch out for with Material UI: See [./QuirksOfMaterialUi.md](./QuirksOfMaterialUi.md)
