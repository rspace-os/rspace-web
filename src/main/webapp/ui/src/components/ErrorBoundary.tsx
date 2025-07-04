import Grid from "@mui/material/Grid";
import React from "react";
import clsx from "clsx";
import { withStyles } from "Styles";
import AnalyticsContext from "@/stores/contexts/Analytics";

/**
 * Error message to display when we cannot recover from an error and we cannot
 * provide any more specific information.
 */
export const ERROR_MSG: React.ReactNode = (
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
  { topOfViewport?: boolean; children: React.ReactNode },
  { topOfViewport: string }
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
}))(
  ({
    topOfViewport,
    classes,
    children,
  }: {
    topOfViewport?: boolean;
    children: React.ReactNode;
    classes: { topOfViewport: string };
  }) => (
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
  )
);

type ErrorBoundaryArgs = {
  message?: string;
  topOfViewport?: boolean;
  children: React.ReactNode;
};

type ErrorBoundaryState = {
  hasError: boolean;
};

/**
 * The ErrorBoundary component is a React component that catches errors in its
 * children and displays an error message instead. This is useful for catching
 * errors that are not recoverable and would otherwise crash the app.
 *
 * If it is rendered inside of an {@link AnalyticsContext} provider, it will
 * also report the error to the analytics service, allowing us to track errors
 * that occur in production.
 */
export default class ErrorBoundary extends React.Component<
  ErrorBoundaryArgs,
  ErrorBoundaryState
> {
  declare context: React.ContextType<typeof AnalyticsContext>;

  message: React.ReactNode;

  constructor(props: ErrorBoundaryArgs) {
    super(props);
    this.state = { hasError: false };
    this.message = props.message ?? ERROR_MSG;
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    this.context.trackEvent("reactRenderFailed", { error, errorInfo });
  }

  componentDidMount(): void {
    if (!this.context) {
      throw new Error(
        "ErrorBoundary must be used within an AnalyticsContext provider"
      );
    }
  }

  render(): React.ReactNode {
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
ErrorBoundary.contextType = AnalyticsContext;
