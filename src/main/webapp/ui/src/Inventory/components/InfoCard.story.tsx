import Box from "@mui/material/Box";
import { ThemeProvider } from "@mui/material/styles";
import type React from "react";
import { makeMockSample } from "../../stores/models/__tests__/SampleModel/mocking";
import materialTheme from "../../theme";
import NameWithBadge from "./NameWithBadge";

export const LAST_LINE = "Last line";

const lastParagraph = `<p>${LAST_LINE}</p>`;
const filler = "wrap check long description ".repeat(80).slice(0, 2000 - lastParagraph.length - "<p></p>".length);
const longName = "long wrapping record name ".repeat(10).slice(0, 255);

/**
 * A sample name with its info badge in the middle of the screen, where the
 * least room is left on either side for the info card. The name and the
 * description are at their caps, 255 and 2000 characters.
 */
export function InfoCardWithLongDescription(): React.ReactNode {
  return (
    <ThemeProvider theme={materialTheme}>
      <Box sx={{ height: "100vh", display: "flex", alignItems: "center" }}>
        <NameWithBadge record={makeMockSample({ name: longName, description: `<p>${filler}</p>${lastParagraph}` })} />
      </Box>
    </ThemeProvider>
  );
}
