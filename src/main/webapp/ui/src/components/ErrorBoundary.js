//@flow strict

import Grid from "@mui/material/Grid";
import React, { type Node } from "react";
import clsx from "clsx";
import { withStyles } from "Styles";

export const ERROR_MSG: Node = (
  <>
    Something went wrong! Please refresh the page. If this error persists,
    please contact{" "}
    <a href="mailto:support@researchspace.com" rel="noreferrer" target="_blank">
      support@researchspace.com
    </a>{" "}
    with details of when the issue happens.
  </>
);

const Container = withStyles<
  {| topOfViewport?: boolean, children: Node |},
  {| topOfViewport: string |}
>(() => ({
  topOfViewport: {
    position: "fixed",
    top: 0,
    left: 0,
    fontSize: 20,
    backgroundColor: "white",
    borderBottom: "2px solid black",
    padding: 20,
    zIndex: 9999 /* higher than anything else */,
    paddingBottom: 0,
  },
}))(({ topOfViewport, classes, children }) => (
  <Grid
    container
    direction="column"
    alignItems="center"
    justifyContent="center"
    className={clsx(
      (topOfViewport === false || topOfViewport === null) &&
        classes.topOfViewport
    )}
  >
    {children}
  </Grid>
));

type ErrorBoundaryArgs = {|
  message?: string,
  topOfViewport?: boolean,
  children: Node,
|};

type ErrorBoundaryState = {|
  hasError: boolean,
|};

export default class ErrorBoundary extends React.Component<
  ErrorBoundaryArgs,
  ErrorBoundaryState
> {
  message: Node;

  constructor(props: ErrorBoundaryArgs) {
    super(props);
    this.state = { hasError: false };
    this.message = props.message ?? ERROR_MSG;
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  render(): Node {
    if (this.state.hasError) {
      return (
        <Container topOfViewport={this.props.topOfViewport}>
          <Grid item>
            <p>{this.message}</p>
          </Grid>
        </Container>
      );
    }

    return this.props.children;
  }
}
