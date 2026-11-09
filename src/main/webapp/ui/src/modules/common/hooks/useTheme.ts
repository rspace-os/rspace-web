import { useUIStore } from "@/modules/common/stores/uiStore";

export function useTheme() {
  const theme = useUIStore((state) => state.theme);
  const setTheme = useUIStore((state) => state.setTheme);
  const toggle = useUIStore((state) => state.toggleTheme);

  return { theme, setTheme, toggle };
}
