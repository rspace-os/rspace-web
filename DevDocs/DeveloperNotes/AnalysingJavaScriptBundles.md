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

From the `src/main/webapp/ui` directory, run:

```bash
npm run build:stats
```

This runs a production build and generates a `stats.html` file in the
`src/main/webapp/ui` directory. Open this file in a browser to see an
interactive treemap of all bundles and their constituent modules. Use the gzip
toggle to see sizes representative of what users will actually download.
