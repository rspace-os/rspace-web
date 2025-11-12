import { FetchHttpClient } from "@effect/platform";
import { Layer } from "effect";

export const RSpaceFetchQueryClient =

export const RSpaceFetchClient = FetchHttpClient.layer.pipe(
  Layer.provide(
    Layer.succeed(FetchHttpClient.RequestInit, {
      headers: {
        "X-Requested-With": "XMLHttpRequest",
      },
    }),
  ),
);
