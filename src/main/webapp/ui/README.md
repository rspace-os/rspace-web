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

## Architecture Migration

The codebase has evolved over time, and as such contains a mix of architectural
patterns. Many of the older pages are still based on JSPs with jQuery, with much
of their JavaScript code in ../scripts, with exception of the React islands
which can be found in [CreateGroup](./src/CreateGroup), [Gallery](./src/Gallery)
(this is the old Gallery), [my-rspace](./src/my-rspace),
[system-groups](./src/system-groups), [system-ror](./src/system-ror), and
[Toolbar](./src/Toolbar).

Inventory, the Gallery, the Apps page, and some other newer parts of the ELN are
fully React and TypeScript and also live in this directory. They can be found in
[Inventory](./src/Inventory), [eln](./src/eln),
[eln-dmp-integration](./src/eln-dmp-integration), and
[eln-inventory-integration](./src/eln-inventory-integration).

Some of the older React code still uses legacy patterns such as class components
and MobX stores, while newer code uses functional components with hooks and React
Context for state management. New code should avoid using MobX stores, and
instead use React Context and custom hooks for state management.

## Testing Strategy

### Component Tests (Playwright)
Most new React tests (since mid-2025) have been written using Playwright's
component testing capabilities. This allows for more realistic testing of React
components in isolation, with the ability to mock network requests and include
necessary context providers. Whilst slower to run than Jest unit tests, they
provide better confidence that components work as expected, in particular when
it comes to accessibility.

Many of the tests have been written using a BDD-style approach, allowing for
the test code to closely align with the feature specification and user stories.

### Unit Tests (Jest)
Much of the rest of the React codebase is still tested using Jest unit tests. These
tests are generally faster to run than Playwright component tests, but can be
less realistic as they often require more mocking and stubbing of dependencies.
The most compelling reason to avoid using Jest, however, is simply how
frustrating it is to assert properties about a UI that you cannot see.

## Further Documentation

For detailed information about specific areas

### Directories
- Type definitions: See [src/stores/definitions/README.md](src/stores/definitions/README.md)
- Model classes: See [src/stores/models/README.md](src/stores/models/README.md)
- React Contexts: See [src/stores/contexts/README.md](src/stores/contexts/README.md)
- Global state management: See [src/stores/stores/README.md](src/stores/stores/README.md)

### Topics
- Link Navigation in Inventory: See [src/Inventory/NavigationInInventory.md](src/Inventory/NavigationInInventory.md)
- Adding Form Fields in Inventory: See [src/Inventory/AddingNewFormFields.md](src/Inventory/AddingNewFormFields.md)
- Public Pages: See [src/components/PublicPages/README.md](src/components/PublicPages/README.md)
- Adding new integrations to the Apps page: See [src/eln/apps/AddingANewIntegration.md](src/eln/apps/AddingANewIntegration.md)
- Odd things to watch out for with Material UI: See [./QuirksOfMaterialUi.md](./QuirksOfMaterialUi.md)
