# Analysing JavaScript Bundles

It can be difficult to gain an insight into what code is contributing to the
size of JavaScript bundles outputted by the build tool. All of the application
code and all of the third-party library packages are bundled together into
minified static assets for deployment. These assets should be kept to a
reasonable size, with code split into sensible groupings to minimise load times.
This isn't a huge issue for RSpace as most of our users will load the site once
and return frequently and so will be served the cached scripts most of the time.
All the same, excessive load times each time that their cache is invalidated or
the server has been updated will lead to poor user experience.

To identify what code is contributing the most towards the size of the assets we
can generate a visualisation using Vite's built-in support for
[rollup-plugin-visualizer](https://www.npmjs.com/package/rollup-plugin-visualizer).

From the repo root, run:

```bash
pnpm run build:stats
```

This runs a production build and generates a `stats.html` file in the
`src/main/webapp/ui` directory. Open this file in a browser to see an
interactive treemap of all bundles and their constituent modules. Use the gzip
toggle to see sizes representative of what users will actually download.

Alongside `stats.html`, the same build writes a machine-readable
`src/main/webapp/ui/dist/stats.json` via the
[vite-bundle-analyzer](https://www.npmjs.com/package/vite-bundle-analyzer)
plugin. Both outputs are only produced when the `FRONTEND_BUILD_STATS`
environment variable is set to `true` (which `pnpm run build:stats` does for
you), so a normal `pnpm run build` is unaffected.

## Automated bundle-size comparison on pull requests

On every pull request that touches frontend code, CI compares the bundle size
of the PR against the base branch and posts the result as a PR comment. This is
wired up in `.github/workflows/lint-and-test.yml`:

1. `bundle-size-pr` builds the PR branch with `pnpm run build:stats` and uploads
   its `dist/stats.json` as an artifact.
2. `bundle-size-base` does the same for the base branch.
3. `bundle-size-report` downloads both artifacts and runs
   [`wojtekmaj/vite-compare-bundle-size`](https://github.com/wojtekmaj/vite-compare-bundle-size),
   which diffs the two `stats.json` files and posts (or updates) a comment
   listing the assets that grew, shrank, were added, or were removed.

Notes:

- The comparison only runs for pull requests opened from a branch in this
  repository, because posting the comment needs a writable `GITHUB_TOKEN` that
  fork PRs are not granted.
