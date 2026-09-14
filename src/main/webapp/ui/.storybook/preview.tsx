import CssBaseline from "@mui/material/CssBaseline";
import { ThemeProvider } from "@mui/material/styles";
import { ArgTypes, Description, Markdown, Stories, Title } from "@storybook/addon-docs/blocks";
import type { Preview } from "@storybook/react-vite";
import theme from "../src/theme";

const docsGuidance =
  "Select an individual story in the sidebar to experiment with its Controls. The examples below run in separate frames so dialogs and other overlays stay inside their example.";

const preview: Preview = {
  tags: ["autodocs"],
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
  },
  decorators: [
    (Story) => (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Story />
      </ThemeProvider>
    ),
  ],
};

export default preview;
