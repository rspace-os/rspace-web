# RSpace Frontend

This is the modern parts of the frontend application for RSpace, built with
React and TypeScript. The application serves both as a Single Page Application
(SPA) for Inventory, Gallery, and the Apps page, and provides React islands
embedded within the ELN (Electronic Laboratory Notebook) JSP-based pages.

The [Vite config](./vite.config.ts) defines multiple entry points for these
different application parts -- see the `bundleEntries` object for the complete
list of bundles and their purposes.

The root [package.json](../../../../package.json) defines the dependencies and
scripts for development, testing, and building the application.

## Architecture

The codebase has evolved over time, and as such contains a mix of architectural
patterns. Many of the older pages are still based on JSPs with jQuery, with much
of their JavaScript code in [../scripts](../scripts), with the exception of the
React islands which can be found in [src/CreateGroup](./src/CreateGroup),
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

| # | Guidance |
| --- | --- |
| 1. | `dist/`: The gitignored output directory for built files. |
| 2. | [src/__tests__](./src/__tests__): Shared test helpers and setup. |
| 3. | [src/assets](./src/assets): Static assets such as images and branding. |
| 4. | [src/components](./src/components): Reusable React components used across the application. |
| 5. | [src/hooks](./src/hooks): Custom React hooks for shared logic, in particular wrappers for calling our API. |
| 6. | [src/stores](./src/stores): State management code, including type definitions, model classes, React Contexts, and global stores. For more information, see the `src/stores/*/README.md` files. |
| 7. | [src/util](./src/util): Utility functions used across the application. |

Some of the older React code still uses legacy patterns such as class components
and MobX stores, while newer code uses functional components with hooks and React
Context for state management. New code should avoid using MobX stores, and
instead use React Context and custom hooks for state management.

## Shared frontend helpers

Paths in this table are relative to `src/`. Keep shared helpers in these locations rather than
copying them into feature modules.

| # | Path | Contract |
| --- | --- | --- |
| 1. | `modules/common/ui/tooltip-icon-link.tsx` | `TooltipIconLink` composes an icon link with a labelled tooltip. |
| 2. | `modules/common/hooks/useAlignedMinute.ts` | `useAlignedMinute` returns epoch milliseconds aligned to minute boundaries, with an optional clock for tests. |
| 3. | `modules/common/queries/queryRetry.ts` | `httpStatusOf`, `isPermanentClientError`, and `retryUnlessClientError` handle retry policy. Permanent 4xx responses stop retries, except 408 and 429; other failures retry while the failure count is below three. |
| 4. | `modules/common/api/apiV2Headers.ts` | `apiV2Headers` and `apiV2JsonHeaders` build authenticated API v2 headers, preserving other initial headers and setting JSON content type in the latter. |

The UI helpers use the existing generated `modules/common/utils/cn-tables.ts`. The root
`postinstall` runs `pnpm cn:build` to generate it from the shared stylesheet.

## Building the code

To build the codebase you will need to have Node.js and pnpm installed. Maven
builds the production frontend bundle by default. This can be quite slow and is
not necessary for frontend development, instead run the maven command with
`-DreactDevMode=true` but in a separate terminal at the repo root run `corepack enable`,
`pnpm install --frozen-lockfile`, and then `pnpm run serve` to start a process that watches
for changes to the React code and recompiles it as necessary. This is much
faster and provides a better development experience, with shorter feedback loops
and source maps for easier debugging.

The frontend uses Vite. `pnpm build` emits hashed production assets into `dist/`.
`pnpm serve` starts the development server with hot module replacement; it does not write a
production bundle to `dist/`. Both use `vite.config.ts`.

Server-rendered pages use the server-side `rst:bundle` tag, which caches and
reads `dist/.vite/manifest.json` to render the required
`modulepreload`, stylesheet, and module script tags for each bundle. Legacy
TinyMCE 5 pages also use `scripts/viteBundleLoader.mjs` plus bundle-name globals
defined in the JSP headers to preload the Vite-built TinyMCE helper and plugin
entrypoints before TinyMCE initialises.

When `-DreactDevMode=true`, JSP-emitted asset URLs stay root-relative
(`/ui/dist/...`). `ViteDevServerProxyServlet` reverse-proxies those requests
from Jetty to the Vite dev server, so the browser never sees the dev server's
origin for HTTP module fetches. The dev server's port can be overridden via
the optional Maven/system flag
`-DviteDevServerOrigin=http://127.0.0.1:5174` (or
`VITE_DEV_SERVER_PORT` env var for the Vite side). The HMR WebSocket is **not**
proxied — the `@vite/client` script connects directly to the dev server using
`hmr.clientPort` from `vite.config.ts`.

### Storybook

Run `pnpm storybook` from the repository root to browse the component catalog,
or `pnpm storybook:build` to produce a static build. The attributed MUI v9
stories live in `src/modules/common/ui/material-ui`; see its `UPSTREAM.md` for
provenance. `pnpm storybook:test` runs the stories in Chromium.

To review the built catalog inside a local RSpace development server, run
`pnpm run storybook:build`, set `dev.storybook.preview.enabled=true` in
`deployment.properties`, and open `/public/storybook` on that instance.
The route serves the WAR's `WEB-INF/storybook` bundle first, falling back to the
gitignored `storybook-static` directory for local development. It is not
registered when the property is false. When enabled, `/public/storybook` is
unauthenticated and must only be exposed on local/dev instances.

Check **STORYBOOK** in Jenkins to build and bundle the catalog in the WAR.
For a local WAR build, run
`mvn clean package -DgenerateReactDist -DgenerateStorybook=true -DskipTests=true`.
The checkbox defaults off. Omitting `generateStorybook`, or setting it to
`false`, excludes Storybook even if a local static build exists. Use a clean
build when changing the option. Bundling does not enable the public route:
set `dev.storybook.preview.enabled=true` in the local/dev instance's
`deployment.properties` to expose it on a local/dev instance. Leave it disabled
on deployed production instances because the route is unauthenticated.

## Testing strategy

Run frontend checks from the repository root.

| # | Runner | Location and command |
| --- | --- | --- |
| 1. | Vitest with jsdom | Unit tests use `*.test.tsx` or the corresponding JS/TS suffixes. [vite.config.ts](./vite.config.ts) owns the setup. Run `pnpm test <file>`. |
| 2. | Vitest Browser Mode | Real-browser component tests use `*.spec.tsx`, with colocated stories and page objects. [vitest.browser.config.ts](./vitest.browser.config.ts) runs Chromium, Firefox, and WebKit through Playwright. Run `pnpm test-browser <file>`. |
| 3. | Playwright end-to-end tests | Use `*.e2e.ts` for browser flows and `*.api.spec.ts` for HTTP tests. See the [E2E README](./src/__tests__/e2e/README.md) for fixture, environment, and execution details. |
| 4. | Storybook | `pnpm storybook:test` runs the stories in Chromium. |

Use Testing Library for component rendering and semantic queries. Shared MSW setup supplies default
handlers; register scenario handlers through the worker or server for the relevant runner. Keep
property-based tests focused on logic that benefits from generated inputs. The repository's
`react-testing-library` and `rspace-browser-tests` playbooks cover test-authoring conventions.

The [CI workflow](../../../../.github/workflows/lint-and-test.yml) defines test jobs, browser-engine
selection, linting, and TypeScript checks. Refer to it for the commands run on pull requests.

## Further Documentation

For detailed information about specific areas

### Directories

| # | Guidance |
| --- | --- |
| 1. | Type definitions: See [src/stores/definitions/README.md](src/stores/definitions/README.md) |
| 2. | Model classes: See [src/stores/models/README.md](src/stores/models/README.md) |
| 3. | React Contexts: See [src/stores/contexts/README.md](src/stores/contexts/README.md) |
| 4. | Global state management: See [src/stores/stores/README.md](src/stores/stores/README.md) |
| 5. | Custom Hooks: See [src/hooks/README.md](src/hooks/README.md) |
| 6. | Gallery: See [src/eln/gallery/README.md](src/eln/gallery/README.md) |
| 7. | Apps Page: See [src/eln/apps/README.md](src/eln/apps/README.md) |
| 8. | Public Pages: See [src/components/PublicPages/README.md](src/components/PublicPages/README.md) |
| 9. | Inventory Context Menu: See [src/Inventory/components/ContextMenu/README.md](src/Inventory/components/ContextMenu/README.md) |

### Topics

| # | Guidance |
| --- | --- |
| 1. | Accessibility: See [../../../../DevDocs/DeveloperNotes/GettingStarted/Accessibility.md](../../../../DevDocs/DeveloperNotes/GettingStarted/Accessibility.md) |
| 2. | Link Navigation in Inventory: See [src/Inventory/NavigationInInventory.md](src/Inventory/NavigationInInventory.md) |
| 3. | Adding Form Fields in Inventory: See [src/Inventory/AddingNewFormFields.md](src/Inventory/AddingNewFormFields.md) |
| 4. | Adding new integrations to the Apps page: See [src/eln/apps/AddingANewIntegration.md](src/eln/apps/AddingANewIntegration.md) |
| 5. | Odd things to watch out for with Material UI: See [./QuirksOfMaterialUi.md](./QuirksOfMaterialUi.md) |
