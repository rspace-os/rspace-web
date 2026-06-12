import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import { Heading } from "@/components/DynamicHeadingLevel";

type TitledBoxArgs = {
  title?: React.ReactNode;
  children?: React.ReactNode;
  allowOverflow?: boolean;
  border?: boolean;
};

function TitledBox({ title, children, allowOverflow = true, border = false }: TitledBoxArgs): React.ReactNode {
  return (
    <Box
      sx={(theme) => ({
        display: "flex",
        flexDirection: "column",
        maxHeight: "100%",
        backgroundColor: "white !important",
        ...(border
          ? {
              border: theme.borders.section,
              my: 1,
              borderRadius: theme.spacing(0.5),
            }
          : {}),
      })}
    >
      {title !== null && typeof title !== "undefined" && (
        <>
          <Box sx={{ px: 2, py: 1 }}>
            <Stack direction="row" sx={{ alignItems: "center" }}>
              <Box sx={{ flexGrow: 1 }}>
                <Heading
                  variant="h5"
                  sx={{
                    wordBreak: "break-word",
                  }}
                >
                  {title}
                </Heading>
              </Box>
            </Stack>
          </Box>
          <Box>
            <Divider orientation="horizontal" />
          </Box>
        </>
      )}
      <Box
        sx={(theme) => ({
          p: theme.spacing(2),
          overflow: "auto",
          overflowX: allowOverflow ? "auto" : "hidden",
        })}
      >
        {children}
      </Box>
    </Box>
  );
}

/**
 * This component defines a box with an optional title and a body.  It is
 * useful in laying out pages that have multiple sections that the user should
 * browse sequentially.
 */
export default observer(TitledBox);
