import { defineConfig } from "vite";
import { browserDefines, sourceAlias } from "../vite.shared.ts";

export default defineConfig(({ command, mode }) => ({
  // Keep built asset URLs rooted at the RSpace development route; standalone
  // Storybook uses its normal root-relative URLs while running locally.
  base: command === "build" ? "/public/storybook/" : "/",
  define: browserDefines(mode),
  resolve: {
    tsconfigPaths: true,
    alias: [sourceAlias],
  },
}));
