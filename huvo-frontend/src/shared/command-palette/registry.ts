import { create } from 'zustand';
import type { LucideIcon } from 'lucide-react';
import type { Role } from '@/shared/types/auth';

/**
 * Global action registry behind the command palette (§6.11 — required, not
 * optional). Features register their own actions; navigation and session
 * actions are registered by the palette itself. Linear is the reference for
 * what "done well" means here.
 */
export interface CommandAction {
  id: string;
  title: string;
  section: string;
  keywords?: string;
  icon?: LucideIcon;
  /** Omitted = visible to every role. */
  roles?: readonly Role[];
  run: () => void;
}

interface PaletteState {
  open: boolean;
  setOpen: (open: boolean) => void;
  toggle: () => void;
  actions: CommandAction[];
  /** Register actions; returns an unregister function (for useEffect cleanup). */
  register: (actions: CommandAction[]) => () => void;
}

export const useCommandPalette = create<PaletteState>((set, get) => ({
  open: false,
  setOpen: (open) => set({ open }),
  toggle: () => set({ open: !get().open }),
  actions: [],
  register: (actions) => {
    set({ actions: [...get().actions, ...actions] });
    return () => {
      const ids = new Set(actions.map((a) => a.id));
      set({ actions: get().actions.filter((a) => !ids.has(a.id)) });
    };
  },
}));
