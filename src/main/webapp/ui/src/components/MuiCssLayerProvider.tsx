import createCache, { type EmotionCache } from "@emotion/cache";
import { StyledEngineProvider } from "@mui/material/styles";
import type { PropsWithChildren, ReactNode } from "react";

type MuiCssLayerProviderProps = PropsWithChildren<unknown>;

export function MuiCssLayerProvider({ children }: MuiCssLayerProviderProps): ReactNode {
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      {children}
    </StyledEngineProvider>
  );
}

export function createMuiCssLayerCache(options: Parameters<typeof createCache>[0]): EmotionCache {
  const cache = createCache(options);
  const originalInsert = cache.insert;

  cache.insert = (...args) => {
    if (!args[1].styles.match(/^@layer\s+[^{}]*$/)) {
      args[1].styles = `@layer mui {${args[1].styles}}`;
    }

    return originalInsert(...args);
  };

  return cache;
}
