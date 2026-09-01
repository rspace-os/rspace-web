import type { StorybookConfig } from "@storybook/tanstack-react";

const config: StorybookConfig = {
  stories: ["../src/modules/**/*.stories.@(ts|tsx)"],
  staticDirs: [
    { from: "../src/__tests__/msw", to: "/" },
    { from: "../../fonts", to: "/fonts" },
  ],
  addons: ["@storybook/addon-a11y", "@storybook/addon-vitest"],
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
