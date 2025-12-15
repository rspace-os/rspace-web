# Stores

This directory contains global state management for Inventory.
[RootStore](./RootStore.ts) is the main entry point, and it contains instances
of all of the other stores. The stores are implemented using
[MobX](https://mobx.js.org/README.html), a popular state management library for
React. React component can access the stores using the `useStores` hook exposed
by [use-stores.ts](../use-stores.ts) and all other code can use `getRootStore()`
from [RootStore.ts](./RootStore.ts).

This is a pattern that has all of the same problems as any global singleton, and so
has not been used in the newer parts of the codebase. New code should instead use
React contexts to provide state to components, as described in
[contexts/README.md](../contexts/README.md) but it is not practical to refactor all of
the existing code to use contexts. Writing jest tests for code that uses the
global store are particularly difficult, and at least as of October 2025 no
effort has been made to attempt to write playwright component tests for such
code.
