import { brandBySlug } from "../lib/brandIcons";

/** Renders a brand logo (simple-icons) for a stored slug, or `fallback` when
 *  the slug is unknown/empty. The glyph inherits the current text color. */
export function BrandLogo({
  slug,
  size = 16,
  fallback = null,
}: {
  slug: string | null;
  size?: number;
  fallback?: React.ReactNode;
}) {
  const icon = brandBySlug(slug);
  if (!icon) return <>{fallback}</>;
  return (
    <svg
      role="img"
      aria-label={icon.title}
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="currentColor"
    >
      <path d={icon.path} />
    </svg>
  );
}
