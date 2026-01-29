import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: [
      { find: /^@\//, replacement: `${path.resolve(__dirname, "src")}/` },
      {
        find: /^Styles$/,
        replacement: path.resolve(__dirname, "src/util/styles.ts"),
      },
      {
        find: /^__mocks__\//,
        replacement: `${path.resolve(__dirname, "__mocks__")}/`,
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
        replacement: path.resolve(__dirname, "src/test-stubs/PhotoswipeStub.js"),
      },
    ],
  },
  test: {
    globals: false,
    environment: "jsdom",
    setupFiles: ["./__tests__/setup.js"],
    include: ["**/?*.test.(js|cjs|mjs|jsx|ts|tsx)"],
    testTimeout: 20000,
    reporters: ["default", "junit", "./__tests__/reporters/slowest-vitest"],
    outputFile: {
      junit: "./junit.xml",
    },
  },
});
