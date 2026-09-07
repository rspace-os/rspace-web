# Upstream attribution

The Material UI component stories in this directory were adapted from
[`laststance/mui-storybook`](https://github.com/laststance/mui-storybook) at
commit [`3e1139552dfb53ade9751ce546d332c984bd338b`](https://github.com/laststance/mui-storybook/tree/3e1139552dfb53ade9751ce546d332c984bd338b).
The upstream project declares the MIT license. Every adapted source file links
to its exact upstream revision.

The stories use RSpace's installed Material UI 9 packages and Storybook's
React/Vite framework. Upstream stories that require `@mui/lab`, plus its
design-token and layout showcases, were not imported.

## Using the reference

Run `pnpm storybook` from the repository root. Each component family has a
generated Docs page with prop descriptions, examples, and source code. Select
an individual story to use its Controls panel. Docs examples run in separate
frames to contain portals, fixed positioning, and repeated example IDs.

The SvgIcon examples use `@mui/icons-material` SVGs. Font-ligature `Icon`
requires a separate icon font stylesheet, which this catalog does not load.

Stories import the installed MUI components directly for rendering and prop
metadata; this catalog does not maintain a separate wrapper API.

Playgrounds expose configurable props. Interactive examples keep their state
in React or synchronize it with Storybook args. Composition stories show how
compound exports fit together and live alongside their parent family.

Run `pnpm storybook:test` for browser interaction and accessibility checks,
or append a family filename to focus the run. Accessibility violations fail
tests. Run `pnpm tsc` and `pnpm lint` after editing stories, and
`pnpm storybook:build` after changing documentation configuration.

These fixtures use the application's RSpace theme. Passing checks cover the
rendered states; they do not replace manual keyboard and visual review of new
states or control combinations.

The application palette is intentionally unchanged. The full suite currently
reports known contrast failures; see [the contrast baseline](CONTRAST.md) for
observed colors, affected families, and reproduction commands. These failures
remain enforced and are not a passing accessibility result.
