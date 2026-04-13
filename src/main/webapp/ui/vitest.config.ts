import path from "node:path";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [
      { find: /^@\//, replacement: `${path.resolve(__dirname, "src")}/` },
      {
        find: /^@mui\/material\/styles$/,
        replacement: path.resolve(
          __dirname,
          "node_modules/@mui/material/node/styles/index.js",
        ),
      },
      {
        find: /^@mui\/x-data-grid$/,
        replacement: path.resolve(
          __dirname,
          "src/test-stubs/MuiDataGridStub.tsx",
        ),
      },
      {
        find: /^Styles$/,
        replacement: path.resolve(__dirname, "src/util/styles.ts"),
      },
      {
        find: /^.+\.css$/,
        replacement: path.resolve(__dirname, "src/test-stubs/CSSStub.js"),
      },
      {
        find: /^.+\.(jpg|png)$/,
        replacement: path.resolve(__dirname, "src/test-stubs/ImageStub.js"),
      },
      {
        find: /^.+\.svg$/,
        replacement: path.resolve(__dirname, "src/test-stubs/SVGStub.js"),
      },
      {
        find: /^react-photoswipe-gallery$/,
        replacement: path.resolve(
          __dirname,
          "src/test-stubs/PhotoswipeStub.js",
        ),
      },
    ],
    externalConditions: ["require"],
  },
  ssr: {
    resolve: {
      externalConditions: ["require"],
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/__tests__/setup.ts"],
    include: ["**/?*.test.(js|cjs|mjs|jsx|ts|tsx)"],
    testTimeout: 20000,
    reporters: ["default", "junit"],
    outputFile: {
      junit: "./junit.xml",
    },
  },
});
