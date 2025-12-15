import React from "react";
import AppBar from ".";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "@/accentedTheme";
import testAccentColor from "../../../__tests__/accentColor.json";

/**
 * A very simple HTML page that includes an app bar for testing purposes.
 */
export function SimplePageWithAppBar(
  props: Partial<React.ComponentProps<typeof AppBar>>
) {
  return (
    <body style={{ margin: 0, padding: 0 }}>
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={createAccentedTheme(testAccentColor)}>
          <AppBar
            variant="page"
            currentPage="Some page"
            accessibilityTips={{}}
            {...props}
          />
          <main>
            <article>Some content here to test the app bar.</article>
          </main>
        </ThemeProvider>
      </StyledEngineProvider>
    </body>
  );
}
