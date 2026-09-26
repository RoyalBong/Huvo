import { useEffect, useMemo } from 'react';
import * as RadixDialog from '@radix-ui/react-dialog';
import { Command } from 'cmdk';
import { Moon, Search, Sun } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { NAV_ITEMS } from '@/app/navigation';
import { useAuthStore } from '@/features/auth/authStore';
import { useTheme } from '@/shared/hooks/useTheme';
import { useCommandPalette, type CommandAction } from '@/shared/command-palette/registry';
import { cn } from '@/shared/utils/cn';

/**
 * Cmd+K command palette (§6.11): every meaningful action is reachable here —
 * navigate any module, toggle theme, sign out. Role-filtered to exactly what
 * the signed-in user's Access Role allows (§5.4).
 */
export function CommandPalette() {
  const open = useCommandPalette((s) => s.open);
  const setOpen = useCommandPalette((s) => s.setOpen);
  const actions = useCommandPalette((s) => s.actions);
  const register = useCommandPalette((s) => s.register);
  const claims = useAuthStore((s) => s.claims);
  const logout = useAuthStore((s) => s.logout);
  const theme = useTheme((s) => s.theme);
  const toggleTheme = useTheme((s) => s.toggle);
  const navigate = useNavigate();

  // Global hotkey — Cmd/Ctrl+K (the baseline SaaS expectation, §6.11).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        useCommandPalette.getState().toggle();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // Built-in actions: navigation, preferences, session.
  useEffect(() => {
    const builtins: CommandAction[] = [
      ...NAV_ITEMS.map((item) => ({
        id: `nav:${item.id}`,
        title: `Go to ${item.label}`,
        section: 'Navigate',
        keywords: item.to,
        icon: item.icon,
        roles: item.roles,
        run: () => navigate(item.to),
      })),
      {
        id: 'theme:toggle',
        title: theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode',
        section: 'Preferences',
        keywords: 'theme appearance dark light mode',
        icon: theme === 'dark' ? Sun : Moon,
        run: () => toggleTheme(),
      },
      {
        id: 'session:logout',
        title: 'Sign out',
        section: 'Session',
        keywords: 'logout sign out log out exit',
        run: () => {
          void logout().then(() => navigate('/login'));
        },
      },
    ];
    return register(builtins);
  }, [register, navigate, theme, toggleTheme, logout]);

  const visibleActions = useMemo(() => {
    if (!claims) return [];
    return actions.filter((a) => !a.roles || a.roles.includes(claims.role));
  }, [actions, claims]);

  const sections = useMemo(() => {
    const map = new Map<string, CommandAction[]>();
    for (const action of visibleActions) {
      const list = map.get(action.section) ?? [];
      list.push(action);
      map.set(action.section, list);
    }
    return [...map.entries()];
  }, [visibleActions]);

  return (
    <RadixDialog.Root open={open} onOpenChange={setOpen}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-50 animate-fade-in bg-black/60 backdrop-blur-sm" />
        <RadixDialog.Content
          className={cn(
            'fixed left-1/2 top-[16%] z-50 w-[calc(100vw-2rem)] max-w-xl -translate-x-1/2',
            'overflow-hidden rounded-card border border-edge shadow-lift animate-fade-up focus:outline-none',
          )}
          aria-label="Command palette"
        >
          <RadixDialog.Title className="sr-only">Command palette</RadixDialog.Title>
          <RadixDialog.Description className="sr-only">
            Navigate and act across Huvo from the keyboard.
          </RadixDialog.Description>

          <Command loop className="bg-surface-1/95 backdrop-blur-xl">
            <div className="flex items-center gap-2.5 border-b border-edge px-4">
              <Search className="h-4 w-4 shrink-0 text-faint" aria-hidden />
              <Command.Input
                placeholder="Type a command or search…"
                className="h-12 w-full bg-transparent text-sm text-fg outline-none placeholder:text-faint"
              />
              <kbd className="hidden rounded border border-edge bg-surface-2 px-1.5 py-0.5 font-mono text-[10px] text-faint sm:block">
                esc
              </kbd>
            </div>

            <Command.List className="max-h-[52vh] overflow-y-auto p-2">
              <Command.Empty className="px-3 py-8 text-center text-sm text-muted">
                No results found.
              </Command.Empty>

              {sections.map(([section, list]) => (
                <Command.Group
                  key={section}
                  heading={
                    <span className="px-2 pb-1 pt-2 text-[11px] font-medium uppercase tracking-wider text-faint">
                      {section}
                    </span>
                  }
                >
                  {list.map((action) => {
                    const Icon = action.icon;
                    return (
                      <Command.Item
                        key={action.id}
                        value={`${action.title} ${action.keywords ?? ''} ${section}`}
                        onSelect={() => {
                          action.run();
                          setOpen(false);
                        }}
                        className={cn(
                          'flex cursor-pointer select-none items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm text-fg outline-none',
                          'data-[selected=true]:bg-surface-2 data-[selected=true]:text-fg',
                        )}
                      >
                        {Icon && <Icon className="h-4 w-4 text-muted" aria-hidden />}
                        <span>{action.title}</span>
                      </Command.Item>
                    );
                  })}
                </Command.Group>
              ))}
            </Command.List>
          </Command>
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}