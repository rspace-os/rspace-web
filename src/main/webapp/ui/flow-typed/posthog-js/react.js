//@flow strict

declare module "posthog-js/react" {
  import type { Node } from "react";
  import typeof Posthog from "posthog-js";

  declare function usePostHog(): {|
    capture: (name: string, properties?: { ... }) => void,
  |};
  declare function PostHogProvider({| client: Posthog, children: Node |}): Node;

}
