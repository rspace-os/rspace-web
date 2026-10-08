import CssBaseline from "@mui/material/CssBaseline";
import { ThemeProvider } from "@mui/material/styles";
import { ArgTypes, Description, Markdown, Stories, Title } from "@storybook/addon-docs/blocks";
import type { Preview } from "@storybook/tanstack-react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import "../src/modules/common/styles/index.css";
import I18nRoot from "../src/modules/common/i18n/I18nRoot";
import theme from "../src/theme";

const docsGuidance =
  "Select an individual story in the sidebar to experiment with its Controls. The examples below run in separate frames so dialogs and other overlays stay inside their example.";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      staleTime: Number.POSITIVE_INFINITY,
    },
  },
});

const preview: Preview = {
  beforeEach: () => {
    queryClient.clear();
  },
  parameters: {
    docs: {
      // Isolate portals, fixed positioning, and repeated example IDs in docs.
      story: { inline: false, height: "400px" },
      page: () => (
        <>
          <Title />
          <Description />
          <Markdown>{docsGuidance}</Markdown>
          <ArgTypes />
          <Stories />
        </>
      ),
    },
    a11y: {
      test: "error",
    },
    controls: {
      expanded: true,
    },
    tanstack: {
      router: {
        context: { queryClient },
      },
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
      const selectedTheme = context.globals.theme as string | undefined;
      document.documentElement.classList.toggle("dark", selectedTheme === "dark");
      const content = context.title.startsWith("Material UI/") ? (
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <Story />
        </ThemeProvider>
      ) : (
        <Story />
      );
      return (
        <QueryClientProvider client={queryClient}>
          <I18nRoot>{content}</I18nRoot>
        </QueryClientProvider>
      );
    },
  ],
};

export default preview;
