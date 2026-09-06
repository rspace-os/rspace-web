/*
 * Adapted from https://github.com/laststance/mui-storybook/blob/3e1139552dfb53ade9751ce546d332c984bd338b/src/components/Paper/examples.tsx
 * Upstream project license: MIT
 */
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import { styled } from "@mui/material/styles";

export function SimplePaper() {
  return (
    <Box
      sx={{
        display: "flex",
        flexWrap: "wrap",
        "& > :not(style)": {
          m: 1,
          width: 128,
          height: 128,
        },
      }}
    >
      <Paper elevation={0} />
      <Paper />
      <Paper elevation={3} />
    </Box>
  );
}

export function Variants() {
  return (
    <Box
      sx={{
        display: "flex",
        "& > :not(style)": {
          m: 1,
          width: 128,
          height: 128,
        },
      }}
    >
      <Paper variant="outlined" />
      <Paper variant="outlined" square />
    </Box>
  );
}

const Item = styled(Paper)(({ theme }) => ({
  ...theme.typography.body2,
  textAlign: "center",
  color: theme.palette.text.secondary,
  height: 60,
  lineHeight: "60px",
}));

export function Elevation() {
  return (
    <Box
      sx={{
        p: 2,
        bgcolor: "background.default",
        display: "grid",
        gridTemplateColumns: { md: "1fr 1fr" },
        gap: 2,
      }}
    >
      {[0, 1, 2, 3, 4, 6, 8, 12, 16, 24].map((elevation) => (
        <Item key={elevation} elevation={elevation}>
          {`elevation=${elevation}`}
        </Item>
      ))}
    </Box>
  );
}
