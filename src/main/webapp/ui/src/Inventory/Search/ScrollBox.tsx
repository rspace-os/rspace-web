import type { SxProps } from "@mui/material";
import Box from "@mui/material/Box";
import type { Property } from "csstype";
import type React from "react";

type ScrollBoxArgs = {
    children: React.ReactNode;
    sx?: SxProps;
    overflowY?: Property.OverflowY;
    overflowX?: Property.OverflowX;
};

export default function ScrollBox({ children, sx, overflowY, overflowX }: ScrollBoxArgs): React.ReactNode {
    return (
        <Box
            sx={{
                overflowY: overflowY ?? "auto",
                overflowX: overflowX ?? "initial",
                ...(sx ?? {}),
            }}
        >
            {children}
        </Box>
    );
}
