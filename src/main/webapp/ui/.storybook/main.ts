import type { StorybookConfig } from "@storybook/react-vite";

const config: StorybookConfig = {
  stories: ["../src/modules/**/*.stories.@(ts|tsx)"],
  staticDirs: [
    // MSW's service worker, shared with the Vitest browser tests, so stories can
    // mount production components that call the API (e.g. the app bar).
    { from: "../src/__tests__/msw", to: "/" },
  ],
  addons: ["@storybook/addon-docs", "@storybook/addon-a11y", "@storybook/addon-vitest"],
  framework: {
    name: "@storybook/react-vite",
    options: {
      builder: {
        viteConfigPath: ".storybook/vite.config.ts",
      },
    },
  },
};

export default config;
