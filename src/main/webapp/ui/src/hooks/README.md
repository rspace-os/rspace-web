# Hooks

This directory contains custom React hooks that represent the modern approach to
state management and side effects in the RSpace frontend. Hooks are used for new
features and are gradually replacing MobX stores and class components throughout
the application.

## Directory Structure

| Directory | Purpose |
|-----------|---------|
| `api/` | Hooks for making API calls and managing server state |
| `auth/` | Authentication-related hooks (token management, OAuth flows) |
| `browser/` | Browser API integrations (localStorage, viewport, URL params) |
| `ui/` | UI interaction patterns (debouncing, drag & drop, accessibility) |
| `websockets/` | Real-time communication via WebSocket connections |

## Design Philosophy

### Modern React Patterns
Hooks in this directory try to follow a consistent style and best practices:
- **Single responsibility** - Keep hooks focused and composable
- **Keep interfaces small** - Prefer multiple simple hooks over one complex hook
- **Mimic shape of JSON API** - Use similar naming and parameters to keep things simple
- **Performance** - Use `useCallback` and `useMemo` judiciously to avoid excessive re-renders
- **Error handling** - Always handle async errors gracefully by utilising other contexts, like alerts
- **Composition over inheritance** for sharing behavior; hooks should build on top of each other

### State Management Hierarchy
Always prefer to keep state as local as possible. The hierarchy of state management approaches is:
1. **Local component state** - useState for component-specific data.
2. **Custom hooks** - reusable stateful logic across components. Create a custom
                      hook when the state is not shared between component but
                      the logic is worth abstracting. Create a hook in this
                      directory when the logic is generic enough to be reused in
                      multiple places, otherwise keep the hook in the
                      component's file or a nearby file.
3. **Prop drilling** - store data on the parent component and pass state down
                       through component props. Provided it is not too messy,
                       this is often the simplest approach.
3. **React Context** - prefer the explicitness of prop drilling, but don't shy
                       away from contexts where appropriate. Often a good idea
                       to define a Provider component and custom hook to access
                       the context, to abstract away implementation details of
                       the context.
4. **MobX stores** - legacy global state (being phased out). Avoid using for all
                     new functionality; prefer app-wide contexts that are not
                     tightly coupled together, can be more easily re-used, and
                     are testable.

## Common Patterns

### API Hooks Pattern
API hooks follow a consistent pattern for async data fetching:

```typescript
import { Fetched } from "@/util/fetchingData";

export function useApiData(id: string): Fetched<DataType> {
  const [state, setState] = React.useState<Fetched<DataType>>({ tag: "loading" });

  React.useEffect(() => {
    // Async data fetching with error handling
    // State wrapped in Fetched<T> type for consistent error handling
  }, [id]);

  return state;
}
```

**Key Features:**
- **Fetched wrapper** - consistent loading/error/success states
- **Automatic token management** - uses `useOauthToken()` for authentication
- **Error boundaries** - proper error handling and user feedback
- **Caching strategies** - avoid unnecessary network calls

### Browser Integration Pattern
Browser hooks encapsulate browser APIs and provide reactive interfaces:

```typescript
export function useBrowserFeature<T>(key: string, defaultValue: T): [T, (value: T) => void] {
  // useState-like interface
  // Automatic synchronization with browser APIs
  // Cleanup on unmount
}
```

### UI Interaction Pattern
UI hooks provide reusable interaction logic:

```typescript
export function useUIPattern<T>(callback: (value: T) => void, options: Options) {
  // Event handling and state management
  // Accessibility considerations
  // Performance optimizations (debouncing, memoization)
}
```
