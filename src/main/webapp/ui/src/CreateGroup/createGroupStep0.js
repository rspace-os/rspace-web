"use strict";
import React from "react";
import Paper from "@mui/material/Paper";
import Grid from "@mui/material/Grid";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";

const createGroupStep0 = (props) => {
  const styles = {
    selectBox: {
      cursor: "pointer",
      padding: "10px",
      marginBottom: "5px",
    },
    container: {
      padding: "0 18px 10px 18px",
    },
    title: {
      textAlign: "center",
    },
    paperContainer: {
      margin: "10px",
    },
  };

  return (
    <div style={styles.paperContainer}>
      <Grid container style={styles.container}>
        <Grid item xs={12} spacing={24}>
          <h3>Choose Group Type</h3>
        </Grid>
      </Grid>
      <Grid container spacing={8} style={styles.container}>
        <Grid item xs={6}>
          <Paper
            onClick={() => props.groupTypeSelect("openLab")}
            style={styles.selectBox}
          >
            <h2 style={styles.title}>Create Open Lab Group</h2>
            <p>
              OpenLab group features:
              <ul>
                <li>
                  All Users can see all work owned by all other members of the
                  lab
                </li>
                <li>
                  The PI’s work is visible to everyone including other PIs
                </li>
                <li>Searches return larger lists of results</li>
                <li>Edit permission must be granted manually</li>
                <li>
                  Best for labs that usually grant members full access to all
                  data
                </li>
              </ul>
            </p>
            <Grid container justifyContent="center">
              <StyledEngineProvider injectFirst>
                <ThemeProvider theme={materialTheme}>
                  <Button
                    variant="contained"
                    color="primary"
                    data-test-id="createGroupChooseOpenLab"
                  >
                    Select
                  </Button>
                </ThemeProvider>
              </StyledEngineProvider>
            </Grid>
          </Paper>
        </Grid>
        <Grid item xs={6}>
          <Paper
            onClick={() => props.groupTypeSelect("labGroup")}
            style={styles.selectBox}
          >
            <h2 style={styles.title}>Create Standard LabGroup</h2>
            <p>
              Standard LabGroup features:
              <ul>
                <li>Only the PI can see all work by default</li>
                <li>PIs work is private by default</li>
                <li>Search only returns results for work you have access to</li>
                <li>Edit AND view permission must be granted manually</li>
                <li>
                  Best for labs where users prefer to keep work private until
                  they are ready to share it
                </li>
              </ul>
            </p>
            <Grid container justifyContent="center">
              <StyledEngineProvider injectFirst>
                <ThemeProvider theme={materialTheme}>
                  <Button
                    variant="contained"
                    color="primary"
                    data-test-id="createGroupChooseLabGroup"
                  >
                    Select
                  </Button>
                </ThemeProvider>
              </StyledEngineProvider>
            </Grid>
          </Paper>
        </Grid>
      </Grid>
    </div>
  );
};

export default createGroupStep0;
