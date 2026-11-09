import { fileURLToPath } from "node:url";
import type { StorybookConfig } from "@storybook/tanstack-react";

const config: StorybookConfig = {
  stories: ["../src/**/*.stories.@(ts|tsx)"],
  staticDirs: [
    { from: "../src/__tests__/msw", to: "/" },
    { from: "../../fonts", to: "/fonts" },
  ],
  addons: ["@storybook/addon-docs", "@storybook/addon-a11y", "@storybook/addon-vitest"],
  // Storybook 10.6's router preview calls the pre-1.171 interpolation API.
  // Keep its navigation mocks; preview.tsx supplies the router instead.
  previewAnnotations: (entries = []) =>
    entries.filter((entry) => entry !== fileURLToPath(import.meta.resolve("@storybook/tanstack-react/preview"))),
  core: {
    disableTelemetry: true,
  },
  framework: {
    name: "@storybook/tanstack-react",
    options: {
      builder: {
        viteConfigPath: ".storybook/vite.config.ts",
      },
    },
  },
};

export default config;
