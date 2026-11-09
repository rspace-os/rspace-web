import { createContext, type ReactNode, useState } from "react";

type Claim = { kind: "parameterPrefix" | "storageKey"; value: string };

export type TableListPageRegistry = {
  /** Claims a persisted table's URL prefix and storage key; returns the release for this instance only. */
  register: (instance: string, table: string, claims: readonly Claim[]) => () => void;
};

const option = { parameterPrefix: "parameterPrefix", storageKey: "tableId" } as const;

export function createTableListPageRegistry(): TableListPageRegistry {
  const owners = new Map<string, Map<string, string>>();
  return {
    register: (instance, table, claims) => {
      for (const { kind, value } of claims) {
        const key = `${kind}:${value}`;
        const claimed = owners.get(key) ?? new Map<string, string>();
        for (const [other, otherTable] of claimed) {
          if (other === instance) continue;
          console.error(
            `TableList "${table}" shares the ${kind} "${value}" with TableList "${otherTable}" on this page, so their ` +
              `views would overwrite each other. Give one of them a distinct \`${option[kind]}\` option.`,
          );
        }
        claimed.set(instance, table);
        owners.set(key, claimed);
      }
      return () => {
        for (const { kind, value } of claims) {
          const key = `${kind}:${value}`;
          const claimed = owners.get(key);
          claimed?.delete(instance);
          if (claimed?.size === 0) owners.delete(key);
        }
      };
    },
  };
}

/** Without a provider nothing is checked, so tests, stories and standalone compositions need no wrapper. */
export const TableListPageRegistryContext = createContext<TableListPageRegistry>({ register: () => () => undefined });

/** Reports persisted tables on one page that share a URL prefix or a storage key. */
export function TableListPageRegistryProvider({ children }: { children: ReactNode }) {
  const [registry] = useState(createTableListPageRegistry);
  return <TableListPageRegistryContext value={registry}>{children}</TableListPageRegistryContext>;
}
