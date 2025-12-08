import { withStyles } from "Styles";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import { darken } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import HeroImage from "/src/assets/graphics/NoActiveResult.svg";
import docLinks from "../../../assets/DocLinks";

const Center = withStyles<{ children: React.ReactNode }, { outer: string; inner: string }>(() => ({
    outer: {
        minHeight: "100vh",
    },
    inner: {
        alignSelf: "center",
    },
}))(({ classes, children }) => (
    <Grid container justifyContent="space-around" className={classes.outer}>
        <Grid item className={classes.inner}>
            {children}
        </Grid>
    </Grid>
));

const Title = () => <Typography variant="h1">Welcome to RSpace Inventory!</Typography>;

const Subtitle = () => (
    <Typography
        variant="subtitle1"
        sx={{
            color: (theme) => darken(theme.palette.primary.main, 0.2),
        }}
    >
        Let&apos;s get you started with the system.
    </Typography>
);

const GetStartedButton = () => (
    <Link
        href={docLinks.gettingStarted}
        target="_blank"
        rel="noreferrer"
        underline="always"
        style={{
            fontSize: "1.4em",
            textUnderlineOffset: "3px",
        }}
    >
        Get Started Guide
    </Link>
);

export default function NoActiveResultPlaceholder(): React.ReactNode {
    return (
        <Center>
            <Grid container direction="column" alignItems="center">
                <Grid item>
                    <Box mb={1} mt={2}>
                        <Title />
                    </Box>
                </Grid>
                <Grid item>
                    <Box mb={3}>
                        <Subtitle />
                    </Box>
                </Grid>
                <Grid item>
                    <Box mb={2}>
                        <GetStartedButton />
                    </Box>
                </Grid>
                <Grid item>
                    <img src={HeroImage} />
                </Grid>
            </Grid>
        </Center>
    );
}
