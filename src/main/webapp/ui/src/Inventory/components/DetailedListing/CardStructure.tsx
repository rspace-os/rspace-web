import React from "react";
import { observer } from "mobx-react-lite";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import { useTheme } from "@mui/material/styles";

function Header({
  strikeThroughTitle,
  ...rest
}: { strikeThroughTitle: boolean } & React.ComponentProps<
  typeof CardHeader
>): React.ReactNode {
  const theme = useTheme();
  return (
    <CardHeader
      {...rest}
      sx={{ p: 0.5 }}
      slotProps={{
        title: {
          sx: {
            fontWeight: 600,
            wordBreak: "break-word",
            textDecorationLine: strikeThroughTitle ? "line-through" : "none",
          },
        },
        subheader: {
          sx: {
            fontSize: "0.8em",
            lineHeight: theme.spacing(1.5),
          },
        },
        avatar: {
          sx: {
            margin: theme.spacing(0, 1.5, 0, 0.75),
          },
        },
        action: {
          sx: {
            alignSelf: "unset",
            margin: 0,
          },
        },
      }}
    />
  );
}

type CardStructureArgs = {
  content: React.ReactNode;
  contentFooter?: React.ReactNode;
  headerAction?: React.ReactNode;
  headerAvatar?: React.ReactNode;
  image: React.ReactNode;
  subheader: React.ReactNode | string;
  title: React.ReactNode;
  onClick?: () => void;
  deleted?: boolean;
  sx?: React.ComponentProps<typeof Card>["sx"];
};

function CardStructure({
  content,
  contentFooter,
  headerAction,
  headerAvatar,
  image,
  subheader,
  title,
  onClick,
  deleted = false,
  sx,
}: CardStructureArgs): React.ReactNode {
  const theme = useTheme();

  return (
    <Card
      sx={
        (sx
          ? [
              {
                height: "100%",
                "&:hover": {
                  backgroundColor: theme.palette.background.default,
                },
              },
              sx,
            ]
          : [
              {
                height: "100%",
                "&:hover": {
                  backgroundColor: theme.palette.background.default,
                },
              },
            ]) as React.ComponentProps<typeof Card>["sx"]
      }
      role="region"
    >
      <Grid container sx={{ height: "100%" }}>
        <Grid
          sx={{
            filter: deleted ? "grayscale(1)" : "none",
            opacity: deleted ? 0.6 : 1,
          }}
          size={4}
        >
          {image}
        </Grid>
        {/* img has separate onClick */}
        <Grid
          onClick={onClick}
          container
          sx={{ flexDirection: "column" }}
          size={8}
        >
          <Grid>
            <Header
              strikeThroughTitle={deleted}
              title={title}
              subheader={subheader}
              avatar={headerAvatar}
              action={headerAction}
            />
          </Grid>
          <Grid>
            <Divider orientation="horizontal" />
          </Grid>
          <Grid>
            <CardContent sx={{ p: 1, pb: 0 }}>{content}</CardContent>
          </Grid>
          {Boolean(contentFooter) && (
            <Grid sx={{ mt: "auto" }}>{contentFooter}</Grid>
          )}
        </Grid>
      </Grid>
    </Card>
  );
}

export default observer(CardStructure);
