import Box from "@mui/material/Box";
import React from "react";
import TransRichText from "@/modules/common/i18n/TransRichText";
import AnalyticsContext from "@/stores/contexts/Analytics";

/**
 * Error message to display when we cannot recover from an error and we cannot
 * provide any more specific information.
 */
export const ERROR_MSG: React.ReactNode = <TransRichText ns="common" i18nKey="errorBoundary.message" />;

/*
 * Last-resort message rendered as a plain string. It is intentionally NOT
 * translated: it only shows when rendering the translated message itself fails
 * (e.g. i18n never initialised), so relying on i18n here would fail too.
 */
const FALLBACK_ERROR_TEXT =
  "Something went wrong! Please refresh the page. If this error persists, please contact support@researchspace.com with details of when the issue happens.";

/*
 * Guards the (translated) error message: if rendering it throws, we fall back to
 * the plain untranslated text rather than letting the ErrorBoundary's own
 * fallback crash and propagate. Error boundaries must be class components.
 */
export class MessageBoundary extends React.Component<{ children: React.ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError(): { failed: boolean } {
    return { failed: true };
  }

  render(): React.ReactNode {
    return this.state.failed ? FALLBACK_ERROR_TEXT : this.props.children;
  }
}

function Container({
  topOfViewport,
  children,
}: {
  topOfViewport?: boolean;
  children: React.ReactNode;
}): React.ReactNode {
  const applyTopOfViewport = topOfViewport === false || topOfViewport === null;
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexDirection: "column",
        ...(applyTopOfViewport && {
          position: "fixed",
          top: 0,
          left: 0,
          fontSize: 20,
          backgroundColor: "white",
          borderBottom: "2px solid black",
          padding: "20px",
          zIndex: 9999 /* higher than anything else */,
          paddingBottom: 0,
        }),
      }}
    >
      {children}
    </Box>
  );
}

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
export default class ErrorBoundary extends React.Component<ErrorBoundaryArgs, ErrorBoundaryState> {
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
      throw new Error("ErrorBoundary must be used within an AnalyticsContext provider");
    }
  }

  render(): React.ReactNode {
    if (this.state.hasError) {
      return (
        <Container topOfViewport={this.props.topOfViewport}>
          <p>
            <MessageBoundary>{this.message}</MessageBoundary>
          </p>
        </Container>
      );
    }

    return this.props.children;
  }
}
ErrorBoundary.contextType = AnalyticsContext;
