# Getting started as a React developer

In general, we use [TypeScript](https://www.typescriptlang.org/) and
[Biome](https://biomejs.dev/) to make development less error prone and more
convenient. Biome is a single fast tool that both formats and lints the frontend,
replacing the previous ESLint + Prettier setup.

## Do
- Type new and modified code with TypeScript. Run `pnpm run tsc` to type-check
  the whole project (`tsc --noEmit`).
- If your editor does not format with Biome on save, run `pnpm run lint:fix` on
  modified files before committing.
- When you add a new npm package that ships its own types, no extra step is
  needed; otherwise install the community types, e.g. `pnpm add -D @types/<package-name>`.

## Notes
- Biome formats and lints JavaScript, TypeScript, JSX/TSX, JSON, and CSS (not Markdown).
- Type-checking is done by `tsc`; Biome handles linting and formatting. Both run
  in CI and in the pre-commit hook, and any Biome info/warning/error fails the
  check (`pnpm run lint:ci`).

## Editor set up

### VSCode
1. Make sure you have the latest version of VS Code.
2. Install the Biome plugin `biomejs.biome`. VSCode will then automatically
   format code on save. You can also trigger formatting manually:
   - Windows `Shift + Alt + F`
   - Mac `Shift + Option + F`
   - Ubuntu `Ctrl + Shift + I`

   The workspace `.vscode/settings.json` already sets Biome as the default
   formatter and enables fix-all / organize-imports on save.

#### VSCode __recommendations__
- Additional plugins:
  1. npm - `eg2.vscode-npm-script`
  1. npm-intellisense - `christian-kohler.npm-intellisense`
- Replace VSCode with VSCodium. VSCodium is a drop-in replacement for VSCode
  without Microsoft telemetry and tracking.

### WebStorm / Intellij
- WebStorm/IntelliJ provide decent TypeScript inspections out of the box.
- Install the Biome plugin from `File > Settings/Preferences > Plugins`, then
  enable "Run Biome on save" (or trigger the "Reformat with Biome" action) so
  formatting and safe lint fixes are applied automatically.

### Other editors
Biome ships a language server. If your editor does not have a dedicated Biome
plugin, point its Language Server Protocol integration at `biome lsp-proxy`, or
run `pnpm run lint` / `pnpm run lint:fix` from the command line.


## General Notes

Some notes on things to watch out for with using React in this codebase.


### Shared app shell

`newApp/index.jsp` supplies the servlet context through `#app`'s escaped
`data-context-path` attribute. The production router uses that value as its
basepath, so its routes and generated navigation URLs retain the deployment
prefix. The empty servlet context uses `/`.

Both app-shell and legacy help actions use `showLighthouse()`. It opens the
widget when its methods are ready, or opens the documentation website when the
external widget is unavailable. Keep help actions available while it loads.

### Application routes

Production routes live under `src/main/webapp/ui/src/routes/`. Each URL segment
has its own directory. Keep page components, search validators, reusable helpers,
tests, and stories in their existing feature modules; route files only declare
routes and private route-specific helpers.

| File relative to `src/routes/` | Purpose |
| --- | --- |
| `__root.tsx` | Required TanStack root exception. Owns AppShell, root metadata, scripts, and the root not-found page. |
| `booking/route.tsx` | Parent route for the Booking layout and inherited context. |
| `booking/index.tsx` | Index page inside the Booking layout. |
| `booking/calendar/index.tsx` | Calendar page. Its sibling `bookings/` directory shares the URL prefix without inheriting Calendar's component or search validator. |
| `booking/calendar/bookings/$id/route.tsx` | Event layout. Its `index.tsx` and `edit/index.tsx` render inside the event's Outlet. |
| `booking/bookable-items/$globalId/{-$tab}/index.tsx` | Dynamic item ID and optional tab, using TanStack's parameter syntax. |

Use TanStack's default `index.tsx` and `route.tsx` filenames. `__root.tsx` is
allowed only at the route-directory root. `pnpm lint:routes` checks this convention
with `@ls-lint/ls-lint`; `pnpm lint` and `pnpm lint:ci` include it. Flat filenames
such as `booking.calendar.tsx`, dotted directories, `page.tsx`, `layout.tsx`, and
helper files in this directory fail the check.
Biome's scoped filename rule also checks full JavaScript/TypeScript filenames,
because ls-lint's extension wildcards match a fixed number of dot-separated
parts. The ls-lint configuration covers filenames with up to three extension
parts; the Biome rule rejects dotted source filenames of any depth.

Index-route IDs include a trailing slash. For example, use
`getRouteApi("/booking/all-items/")` or `useSearch({ from: "/booking/all-items/" })`
for that page. Use the generated types for route IDs and navigation destinations;
do not cast paths to suppress errors. An index page does not make its component
or validator the parent of deeper URL paths.

After adding, moving, or renaming a route, start `pnpm serve` or run `pnpm build`
from the repository root. The Vite router plugin generates
`src/main/webapp/ui/src/routeTree.gen.ts` and updates route declarations. Review
and commit both alongside the route changes. Do not edit the generated tree by
hand; Biome excludes it. CI rebuilds the frontend and fails if regeneration
changes the committed tree or declarations. The committed tree lets standalone
`pnpm tsc`, unit tests, Browser Mode, and Storybook run without starting a
generator watcher.

The router plugin runs before React in the application's Vite configuration and
enables automatic component splitting for development and production builds.
Root routes, search validation, and route context stay eager. Avoid exporting
components or reusable values from route files, and use `getRouteApi` in feature
components instead of importing their route declaration. Shared eager imports
can pull a page into the initial bundle; inspect `pnpm build:stats` when changing
these dependencies. Unit tests use the committed declarations without the
splitting plugin, as do the separate Browser Mode and Storybook configurations.

### Lazy loading components

React has a mechanism for lazily loading sections of the UI, so that only users
who use a part of the UI incur the costs of downloading the code that makes
that part of the UI work. This is implemented using the `lazy` function and the
`Suspense` component. This technique is used a few times in the codebase,
especially where some feature is implemented using a library such as editing
images or visualising data on a map.

One thing to note about this technique, however, is that if ANY other part of
the codebase imports the lazily loaded code in the usual manner then it causes
the bundler to entirely ignore the lazy loading. This does make some sense as if
the code has already been loaded in the main bundle then there is no need to
dynamically loaded the same code again but it does mean that it is very easy to
inadvertently break some lazy loading through spooky action at a distance.

Let's say we have a component called BigInfrequentlyUsedFeature that is lazy loaded
```
  const LazyBigInfrequentlyUsedFeature = lazy(() => import("./BigInfrequentlyUsedFeature"));
```
This will dynamically create a new JS bundle in ../../../src/main/webapp/ui/dist
that will be fetched only once `LazyBigInfrequentlyUsedFeature` is rendered.
However, if some other code imports BigInfrequentlyUsedFeature in the usual
manner then it will be included in the main bundle and the new bundle will not
be generated. In other words, if a component is EVER imported lazily then it
MUST ALWAYS be imported lazily (either directly or indirectly). This is a
problem as it may not always be immediately obvious if a given component (or any
component that it relies all the way down the component tree) is being lazily
loaded somewhere else in the codebase.

If that wasn't bad enough, the component itself doesn't even have to be
imported; if ANYTHING is imported in the usual manner from the same module as
the lazily loaded component then it will invalidate the lazily loading. Let's
say that the module `BigInfrequentlyUsedFeature`, in addition to the default
export of the `BigInfrequentlyUsedFeature` component itself, also exports as
constant `SOME_CONSTANT`. If `SOME_CONSTANT` is imported ANYWHERE by code in the
main bundle then the lazily loading will not work.

All of this is to say, that whilst lazy loading components is a powerful
feature, and something we ought to use more to keep the bundle sizes small, it
is also a feature that doens't have any guardrails. Which components are lazily
loaded must be carefully evaluated to minimise the risk that the additional
complexity in the code results in no change to page load performance simply
because the lazy loading is just not happening.
