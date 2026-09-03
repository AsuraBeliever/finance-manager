import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { GripVertical } from "lucide-react";
import { type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { es } from "../i18n/es";
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
// Phone-width order, kept apart from the grid layout above because the two are
// different shapes: the grid places widgets in columns, this is a plain list of
// keys. The Android app reorders the same setting, so a stack arranged on the
// phone reads back the same here.
const ORDER_KEY = "dashboardOrder";
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
  /** Fixed pixel height for the phone stack. Needed by chart widgets, whose
   *  ResponsiveContainer needs a definite height. Omit for content widgets,
   *  which grow to fit. */
  mobileHeight?: number;
  /** Ceiling for the phone stack: the widget grows to fit and scrolls inside
   *  once it would pass this. A fixed height here would leave dead space
   *  under a short list, which is what expense-by-category usually is. */
  mobileMaxHeight?: number;
  node: ReactNode;
}

function parseOrder(raw: string | null | undefined): string[] {
  try {
    const parsed = JSON.parse(raw ?? "[]");
    return Array.isArray(parsed) ? (parsed as string[]) : [];
  } catch {
    return [];
  }
}

/**
 * Apply the saved phone order: the keys it mentions first, in the order it
 * lists them, then anything it does not mention — a widget added since the
 * order was saved, or one that only appears once its data loads — appended at
 * the end, keeping the natural order among themselves.
 */
function orderedForPhone(items: GridItemSpec[], raw: string | null | undefined): GridItemSpec[] {
  const order = parseOrder(raw);
  if (order.length === 0) return items;
  const rank = new Map(order.map((key, index) => [key, index]));
  return [...items].sort(
    (a, b) =>
      (rank.get(a.key) ?? Number.MAX_SAFE_INTEGER) - (rank.get(b.key) ?? Number.MAX_SAFE_INTEGER),
  );
}

/** One card of the phone stack, draggable from its grip. */
function PhoneCard({ item }: { item: GridItemSpec }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.key,
  });
  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        ...(item.mobileHeight ? { height: item.mobileHeight } : {}),
        ...(item.mobileMaxHeight ? { maxHeight: item.mobileMaxHeight } : {}),
      }}
      className={`group/cell relative ${isDragging ? "z-10 opacity-75" : ""}`}
    >
      <button
        type="button"
        aria-label={es.dashboard.reorder}
        {...attributes}
        {...listeners}
        // touch-none, or the browser scrolls the page instead of starting the drag.
        className="dash-drag touch-action-reveal absolute right-2.5 top-2.5 z-10 touch-none cursor-grab rounded-md p-1 text-fg-subtle transition-opacity hover:bg-surface-overlay hover:text-fg active:cursor-grabbing"
      >
        <GripVertical size={16} />
      </button>
      {item.node}
    </div>
  );
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

/** The active breakpoint for a container width — mirrors RGL's own
 *  getBreakpointFromWidth (highest breakpoint whose threshold is below width). */
function activeBreakpoint(width: number): string {
  let best = "xxs";
  let bestVal = -Infinity;
  for (const [bp, min] of Object.entries(BREAKPOINTS)) {
    if (width > min && min > bestVal) {
      best = bp;
      bestVal = min;
    }
  }
  return best;
}

function parse(raw: string | null | undefined): ResponsiveLayouts | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as ResponsiveLayouts;
  } catch {
    return null;
  }
}

/** Reconcile a saved layout with the current item set: drop positions for items
 *  that no longer exist and append any new item (e.g. a renamed widget) at the
 *  bottom with its default size. Without this, a child the saved layout doesn't
 *  cover gets RGL's 1×1 fallback and effectively disappears — which is exactly
 *  what happens to existing users when a widget key changes. */
function reconcile(layouts: ResponsiveLayouts, base: LayoutItem[]): ResponsiveLayouts {
  const known = new Set(base.map((b) => b.i));
  const out: ResponsiveLayouts = {};
  for (const [bp, items] of Object.entries(layouts)) {
    if (!items) continue;
    const present = new Set(items.map((it) => it.i));
    const kept = items.filter((it) => known.has(it.i));
    const maxY = kept.reduce((m, it) => Math.max(m, it.y + it.h), 0);
    const missing = base
      .filter((b) => !present.has(b.i))
      .map((b, i) => ({ ...b, x: 0, y: maxY + i }));
    out[bp] = [...kept, ...missing];
  }
  return out;
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
  // Stable signature of the present widgets. Conditional widgets (budget, goals,
  // subscriptions) load from their own async queries and appear after the grid
  // first mounts; when the set changes we must re-adopt the saved layout so a
  // late-arriving widget keeps its saved size instead of getting the default.
  const keySig = useMemo(() => items.map((it) => it.key).join("|"), [items]);

  const queryClient = useQueryClient();
  // Source of truth: the per-user setting. Refetch on mount so a change made on
  // another device is picked up; the persisted cache gives an instant first paint.
  // Phone order, read alongside the grid layout so the mobile branch below has
  // it ready without a second render pass.
  const savedOrder = useQuery({
    queryKey: ["dashboardOrder"],
    queryFn: () => getSetting(ORDER_KEY),
    staleTime: 0,
  });

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

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
  const widthRef = useRef(width);
  widthRef.current = width;

  // Save: keep the query cache in sync immediately (so a reload reads the new
  // layout from cache, not the stale previous value) and write to the server
  // debounced (once per gesture).
  const save = useRef((next: ResponsiveLayouts) => {
    const serialized = JSON.stringify(next);
    queryClient.setQueryData(["dashboardLayout"], serialized);
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => setSetting(SETTING_KEY, serialized), 600);
  });

  // Adopt the server layout on load, on refetch, and whenever the set of present
  // widgets changes (a conditional widget loaded late) — UNTIL the user changes
  // something this session. This restores the saved arrangement even if the
  // first paint used a stale cached value (or the default), and re-reconciles
  // against the now-complete base so late-arriving widgets keep their saved size.
  useEffect(() => {
    if (touched.current || !saved.isSuccess) return;
    const parsed = parse(saved.data);
    const next = parsed ? reconcile(parsed, baseRef.current) : { lg: baseRef.current };
    layoutsRef.current = next;
    setLayouts(next);
  }, [saved.isSuccess, saved.data, keySig]);

  // Save the phone order: cache first (so the next paint uses it) then persist.
  const saveOrder = useRef((keys: string[]) => {
    const serialized = JSON.stringify(keys);
    queryClient.setQueryData(["dashboardOrder"], serialized);
    setSetting(ORDER_KEY, serialized);
  });

  // Reset: snap to defaults and persist (so every device resets too). Clears the
  // phone order alongside the grid layout — the button promises one reset, and
  // the Android app clears both from its own button too.
  const firstReset = useRef(resetSignal);
  useEffect(() => {
    if (resetSignal === firstReset.current) return;
    const reset = { lg: baseRef.current };
    touched.current = true;
    layoutsRef.current = reset;
    setLayouts(reset);
    save.current(reset);
    saveOrder.current([]);
  }, [resetSignal]);

  // We drive RGL as a controlled component only when WE change the layout
  // (adopt/reset/user save). RGL otherwise owns its internal layout; we don't
  // echo its onLayoutChange back into `layouts`, because re-feeding the prop
  // makes RGL regenerate from the stale value and its `all` argument then lags
  // a gesture behind (it would persist the PRE-resize size). So onLayoutChange
  // is intentionally a no-op here.
  const onLayoutChange = (_current: Layout, _all: ResponsiveLayouts) => {};

  // A finished drag/resize is the only thing worth saving. The stop callback's
  // first argument is the FINAL layout for the active breakpoint (unlike the
  // onLayoutChange `all`, which lags); merge it over the other breakpoints and
  // persist.
  const onUserChange = (current: Layout) => {
    touched.current = true;
    const bp = activeBreakpoint(widthRef.current);
    const merged: ResponsiveLayouts = { ...(layoutsRef.current ?? {}), [bp]: current };
    layoutsRef.current = merged;
    setLayouts(merged);
    save.current(merged);
  };

  if (layouts === null) {
    return <div ref={containerRef} className="min-h-24" />;
  }

  // On phones (narrow container) the draggable fixed-height grid forces widgets
  // to scroll when their content doesn't match the cell. Instead, stack them at
  // their natural height: content widgets grow to fit (no scroll, no drag) and
  // only the ones with an explicit mobileHeight (charts, expense-by-category)
  // get a set height.
  const isMobile = width > 0 && width < BREAKPOINTS.sm;

  if (isMobile) {
    const stacked = orderedForPhone(items, savedOrder.data);

    // Dropping writes the same per-user setting the Android app reorders, so a
    // stack arranged on either phone reads back the same on the other.
    const onDragEnd = ({ active, over }: DragEndEvent) => {
      if (!over || active.id === over.id) return;
      const current = stacked.map((it) => it.key);
      const from = current.indexOf(String(active.id));
      const to = current.indexOf(String(over.id));
      if (from < 0 || to < 0) return;
      const next = arrayMove(current, from, to);
      // Keys this render does not show (a widget whose data is empty for the
      // period) keep their place at the end instead of being dropped.
      const extra = parseOrder(savedOrder.data).filter((k) => !next.includes(k));
      saveOrder.current([...next, ...extra]);
    };

    return (
      <div ref={containerRef}>
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
          <SortableContext
            items={stacked.map((it) => it.key)}
            strategy={verticalListSortingStrategy}
          >
            <div className="flex flex-col gap-4">
              {stacked.map((it) => (
                <PhoneCard key={it.key} item={it} />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      </div>
    );
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
                aria-label={es.dashboard.reorder}
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
