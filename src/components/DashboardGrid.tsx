import { useQuery, useQueryClient } from "@tanstack/react-query";
import { GripVertical } from "lucide-react";
import { type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import {
  ResponsiveGridLayout,
  useContainerWidth,
  verticalCompactor,
  type Layout,
  type LayoutItem,
  type ResponsiveLayouts,
} from "react-grid-layout";
import "react-grid-layout/css/styles.css";
import { getSetting, setSetting } from "../lib/api";

// Per-user setting key — synced across all devices (server-side).
const SETTING_KEY = "dashboardLayout";
const COLS = { lg: 12, md: 12, sm: 6, xs: 4, xxs: 2 };
const BREAKPOINTS = { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 };
const ROW_HEIGHT = 72;
const MARGIN: [number, number] = [16, 16];

export interface GridItemSpec {
  key: string;
  /** Default size in grid units (cols/rows). */
  w: number;
  h: number;
  minW?: number;
  minH?: number;
  node: ReactNode;
}

/** Shelf-packs the items into the 12-col grid for a sensible default order. */
function defaultLayout(items: GridItemSpec[]): LayoutItem[] {
  const cols = COLS.lg;
  let x = 0;
  let y = 0;
  let rowH = 0;
  return items.map((it) => {
    const w = Math.min(it.w, cols);
    if (x + w > cols) {
      x = 0;
      y += rowH;
      rowH = 0;
    }
    const item: LayoutItem = { i: it.key, x, y, w, h: it.h, minW: it.minW ?? 2, minH: it.minH ?? 2 };
    x += w;
    rowH = Math.max(rowH, it.h);
    return item;
  });
}

function parse(raw: string | null | undefined): ResponsiveLayouts | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as ResponsiveLayouts;
  } catch {
    return null;
  }
}

/** A draggable, resizable dashboard grid (react-grid-layout v2, React 19 safe).
 *  Each cell drags from its grip handle (so controls/links inside keep working)
 *  and resizes from the corner. The arrangement is saved per user on the server,
 *  so it follows you across web, desktop and phone. */
export function DashboardGrid({
  items,
  resetSignal,
}: {
  items: GridItemSpec[];
  /** Bump to clear the saved layout and snap back to defaults. */
  resetSignal: number;
}) {
  const { width, containerRef } = useContainerWidth();
  const base = useMemo(() => defaultLayout(items), [items]);

  const queryClient = useQueryClient();
  // Source of truth: the per-user setting. Refetch on mount so a change made on
  // another device is picked up; the persisted cache gives an instant first paint.
  const saved = useQuery({
    queryKey: ["dashboardLayout"],
    queryFn: () => getSetting(SETTING_KEY),
    staleTime: 0,
  });

  const [layouts, setLayouts] = useState<ResponsiveLayouts | null>(null);
  const layoutsRef = useRef<ResponsiveLayouts | null>(null);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const touched = useRef(false); // the user has dragged/resized this session
  const baseRef = useRef(base);
  baseRef.current = base;

  // Save: keep the query cache in sync immediately (so a reload reads the new
  // layout from cache, not the stale previous value) and write to the server
  // debounced (once per gesture).
  const save = useRef((next: ResponsiveLayouts) => {
    const serialized = JSON.stringify(next);
    queryClient.setQueryData(["dashboardLayout"], serialized);
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => setSetting(SETTING_KEY, serialized), 600);
  });

  // Adopt the server layout on load and on refetch, UNTIL the user changes
  // something this session. This restores the saved arrangement even if the
  // first paint used a stale cached value (or the default).
  useEffect(() => {
    if (touched.current || !saved.isSuccess) return;
    const next = parse(saved.data) ?? { lg: baseRef.current };
    layoutsRef.current = next;
    setLayouts(next);
  }, [saved.isSuccess, saved.data]);

  // Reset: snap to defaults and persist (so every device resets too).
  const firstReset = useRef(resetSignal);
  useEffect(() => {
    if (resetSignal === firstReset.current) return;
    const reset = { lg: baseRef.current };
    touched.current = true;
    layoutsRef.current = reset;
    setLayouts(reset);
    save.current(reset);
  }, [resetSignal]);

  // Keep UI state in sync with RGL's own changes (mount, compaction, new cells)
  // without saving — those aren't user intent.
  const onLayoutChange = (_current: Layout, all: ResponsiveLayouts) => {
    layoutsRef.current = all;
    setLayouts(all);
  };

  // Only a real drag/resize is a user preference worth saving.
  const onUserChange = () => {
    touched.current = true;
    if (layoutsRef.current) save.current(layoutsRef.current);
  };

  if (layouts === null) {
    return <div ref={containerRef} className="min-h-24" />;
  }

  return (
    <div ref={containerRef} className="-mx-1">
      {width > 0 && (
        <ResponsiveGridLayout
          width={width}
          layouts={layouts}
          breakpoints={BREAKPOINTS}
          cols={COLS}
          rowHeight={ROW_HEIGHT}
          margin={MARGIN}
          compactor={verticalCompactor}
          dragConfig={{ handle: ".dash-drag" }}
          onLayoutChange={onLayoutChange}
          onDragStop={onUserChange}
          onResizeStop={onUserChange}
        >
          {items.map((it) => (
            <div key={it.key} className="group/cell relative h-full">
              <button
                type="button"
                aria-label="Mover"
                className="dash-drag touch-action-reveal absolute right-2.5 top-2.5 z-10 cursor-grab rounded-md p-1 text-fg-subtle transition-opacity hover:bg-surface-overlay hover:text-fg active:cursor-grabbing"
              >
                <GripVertical size={16} />
              </button>
              {it.node}
            </div>
          ))}
        </ResponsiveGridLayout>
      )}
    </div>
  );
}
