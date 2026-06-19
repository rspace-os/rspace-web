import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import type React from "react";
import testAccentColor from "@/__tests__/accentColor.json";
import createAccentedTheme from "@/accentedTheme";
import AppBar from ".";

/**
 * A very simple HTML page that includes an app bar for testing purposes.
 *
 * The wrapper is a plain <div>, not a <body>: a <body> rendered into the
 * testing-library container <div> is invalid HTML ("<body> cannot be a child
 * of <div>") and is special-cased by React 19. When the account menu opens,
 * MUI's Modal mutates the real document.body, which conflicts with the
 * rendered <body> and makes userEvent.click's act() never settle, hanging the
 * test forever. A <div> wrapper avoids that entirely.
 */
export function SimplePageWithAppBar(props: Partial<React.ComponentProps<typeof AppBar>>) {
  return (
    <div style={{ margin: 0, padding: 0 }}>
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={createAccentedTheme(testAccentColor)}>
          <AppBar variant="page" currentPage="Some page" accessibilityTips={{}} {...props} />
          <main>
            <article>Some content here to test the app bar.</article>
          </main>
        </ThemeProvider>
      </StyledEngineProvider>
    </div>
  );
}
