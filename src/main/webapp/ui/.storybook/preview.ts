import type { Preview } from "@storybook/react-vite";
import "../src/modules/common/styles/index.css";

const preview: Preview = {
  parameters: {
    a11y: {
      test: "error",
    },
  },
  globalTypes: {
    theme: {
      description: "Global theme",
      defaultValue: "light",
      toolbar: {
        title: "Theme",
        icon: "circlehollow",
        items: [
          { value: "light", title: "Light" },
          { value: "dark", title: "Dark" },
        ],
        dynamicTitle: true,
      },
    },
  },
  decorators: [
    (Story, context) => {
      const theme = context.globals.theme as string | undefined;
      document.documentElement.classList.toggle("dark", theme === "dark");
      return Story();
    },
  ],
};

export default preview;
