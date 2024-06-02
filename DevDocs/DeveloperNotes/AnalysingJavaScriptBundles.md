# Analysing JavaScript Bundles

It can be difficult to gain an insight into what code is contributing to the
size of JavaScript bundles outputted by Webpack. All of the application code
and all of the third-party library packages are bundled together into minified
static assets for deployment. These assets should be kept to a reasonable size,
with code split into sensible groupings to minimise load times. This isn't a
huge issue for RSpace as most of our users will load the site once and return
frequently and so will be served the cached scripts most of the time. All the
same, excessive load times each time that their cache is invalidated or the
server has been updated will lead to poor user experience.

To identify what code is contributing the most towards the size of the assets
we can generate a visualisation of the relative code sizes with the use of the
[webpack-bundle-analyzer](https://www.npmjs.com/package/webpack-bundle-analyzer)
package. The installation instructions are relatively straightforward but to
simplify the process even further included below is a patch file that can be
applied to the codebase by running the following command from the root of the
repository.

```
patch -p1 < $FILE
```

where $FILE is a file containing the following patch:
```
diff --git a/src/main/webapp/ui/package.json b/src/main/webapp/ui/package.json
index d85ecb675d..d2ad9d8957 100644
--- a/src/main/webapp/ui/package.json
+++ b/src/main/webapp/ui/package.json
@@ -44,6 +44,7 @@
     "prettier": "=2.6.2",
     "style-loader": "=3.3.1",
     "webpack": "=5.72.0",
+    "webpack-bundle-analyzer": "^4.4.1",
     "webpack-cli": "=4.9.2"
   },
   "dependencies": {
diff --git a/src/main/webapp/ui/webpack.config.js b/src/main/webapp/ui/webpack.config.js
index 83418e4e77..75f94e3d42 100644
--- a/src/main/webapp/ui/webpack.config.js
+++ b/src/main/webapp/ui/webpack.config.js
@@ -1,5 +1,6 @@
 const path = require("path");
 const { CleanWebpackPlugin } = require("clean-webpack-plugin");
+const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;

 module.exports = {
   entry: {
@@ -43,7 +44,10 @@ module.exports = {
     path: path.resolve(__dirname, "dist"),
     publicPath: "/ui/dist/",
   },
-  plugins: [new CleanWebpackPlugin()],
+  plugins: [
+    new CleanWebpackPlugin(),
+    new BundleAnalyzerPlugin()
+  ],
   optimization: {
     runtimeChunk: "single",
   },
```

Then run the following two commands:
```
npm install
npm run build
```

This should run a production build and output minified bundles.

Once the build completes a browser window should automatically open displaying
an interactive visualisation. Finally, from the left sidebar I would recommend
selecting gzipped to ensure that the numbers are representative of the size of
the assets that users will be downloading.
