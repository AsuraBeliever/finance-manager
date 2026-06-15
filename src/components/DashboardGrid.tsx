import { GripVertical } from "lucide-react";
import { type ReactNode, useMemo, useState } from "react";
import {
  ResponsiveGridLayout,
  useContainerWidth,
  verticalCompactor,
  type Layout,
  type LayoutItem,
  type ResponsiveLayouts,
} from "react-grid-layout";
import "react-grid-layout/css/styles.css";

const STORAGE_KEY = "finanzas.dashboardLayout.v1";
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

function loadSaved(): ResponsiveLayouts | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as ResponsiveLayouts) : null;
  } catch {
    return null;
  }
}

/** A draggable, resizable dashboard grid (react-grid-layout v2, React 19 safe).
 *  Each cell drags from its grip handle (so controls/links inside keep working)
 *  and resizes from the corner. The arrangement is saved per device. */
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
  const [layouts, setLayouts] = useState<ResponsiveLayouts>(() => loadSaved() ?? { lg: base });
  const [seenReset, setSeenReset] = useState(resetSignal);

  if (resetSignal !== seenReset) {
    setSeenReset(resetSignal);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
    setLayouts({ lg: base });
  }

  const onLayoutChange = (_current: Layout, all: ResponsiveLayouts) => {
    setLayouts(all);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(all));
    } catch {
      /* ignore quota / private-mode errors */
    }
  };

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
        >
          {items.map((it) => (
            <div key={it.key} className="group/cell relative h-full">
              <button
                type="button"
                aria-label="Mover"
                className="dash-drag absolute right-2.5 top-2.5 z-10 cursor-grab rounded-md p-1 text-fg-subtle opacity-0 transition-opacity hover:bg-surface-overlay hover:text-fg active:cursor-grabbing group-hover/cell:opacity-100"
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
