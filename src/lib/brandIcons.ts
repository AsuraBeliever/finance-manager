// Brand logos for subscriptions, via simple-icons. Only the icons we map are
// imported (tree-shaken), so the bundle cost stays small. When a subscription's
// name matches a brand, we store the brand slug in `icon` and tint it with the
// brand color; unmatched names fall back to an initial.
import {
  siApple,
  siApplearcade,
  siApplemusic,
  siApplenews,
  siApplepodcasts,
  siAppletv,
  siAudible,
  siBackblaze,
  siBandcamp,
  siClaude,
  siCoursera,
  siCrunchyroll,
  siDeezer,
  siDigitalocean,
  siDiscord,
  siDropbox,
  siDuolingo,
  siEpicgames,
  siExpressvpn,
  siFigma,
  siGithub,
  siGoogle,
  siGooglecloud,
  siGoogleplay,
  siGrammarly,
  siHbo,
  siHbomax,
  siIcloud,
  siJetbrains,
  siKick,
  siMedium,
  siMega,
  siMubi,
  siNetflix,
  siNetlify,
  siNordvpn,
  siNotion,
  siObsidian,
  siPandora,
  siParamountplus,
  siPatreon,
  siPlaystation,
  siPlex,
  siProton,
  siProtonvpn,
  siRevolut,
  siRiotgames,
  siShowtime,
  siSoundcloud,
  siSpotify,
  siStarz,
  siSteam,
  siTidal,
  siTwitch,
  siUdemy,
  siVercel,
  siVimeo,
  siWise,
  siYoutube,
  siYoutubemusic,
  siZoom,
} from "simple-icons";

export interface Brand {
  slug: string;
  title: string;
  hex: string; // brand color without '#'
  path: string; // SVG path (viewBox 0 0 24 24)
}

interface Entry {
  icon: Brand;
  keywords: string[];
}

// Ordered: more specific names first (e.g. "apple music" before "apple") so the
// first keyword hit wins.
const ENTRIES: Entry[] = [
  { icon: siApplemusic, keywords: ["apple music"] },
  { icon: siAppletv, keywords: ["apple tv"] },
  { icon: siApplearcade, keywords: ["apple arcade"] },
  { icon: siApplepodcasts, keywords: ["apple podcasts"] },
  { icon: siApplenews, keywords: ["apple news"] },
  { icon: siIcloud, keywords: ["icloud", "icloud+"] },
  { icon: siYoutubemusic, keywords: ["youtube music", "yt music"] },
  { icon: siYoutube, keywords: ["youtube", "youtube premium", "yt premium"] },
  { icon: siHbomax, keywords: ["hbo max", "hbomax"] },
  { icon: siHbo, keywords: ["hbo"] },
  { icon: siSpotify, keywords: ["spotify"] },
  { icon: siNetflix, keywords: ["netflix"] },
  { icon: siCrunchyroll, keywords: ["crunchyroll", "crunchy"] },
  { icon: siParamountplus, keywords: ["paramount", "paramount+"] },
  { icon: siStarz, keywords: ["starz"] },
  { icon: siShowtime, keywords: ["showtime"] },
  { icon: siMubi, keywords: ["mubi"] },
  { icon: siPlex, keywords: ["plex"] },
  { icon: siVimeo, keywords: ["vimeo"] },
  { icon: siTidal, keywords: ["tidal"] },
  { icon: siDeezer, keywords: ["deezer"] },
  { icon: siSoundcloud, keywords: ["soundcloud"] },
  { icon: siBandcamp, keywords: ["bandcamp"] },
  { icon: siPandora, keywords: ["pandora"] },
  { icon: siAudible, keywords: ["audible"] },
  { icon: siPlaystation, keywords: ["playstation", "ps plus", "ps+", "psn"] },
  { icon: siSteam, keywords: ["steam"] },
  { icon: siEpicgames, keywords: ["epic games", "epic"] },
  { icon: siRiotgames, keywords: ["riot", "riot games"] },
  { icon: siTwitch, keywords: ["twitch"] },
  { icon: siKick, keywords: ["kick"] },
  { icon: siDiscord, keywords: ["discord", "nitro"] },
  { icon: siNotion, keywords: ["notion"] },
  { icon: siObsidian, keywords: ["obsidian"] },
  { icon: siGithub, keywords: ["github", "copilot"] },
  { icon: siFigma, keywords: ["figma"] },
  { icon: siJetbrains, keywords: ["jetbrains", "intellij"] },
  { icon: siGrammarly, keywords: ["grammarly"] },
  { icon: siCoursera, keywords: ["coursera"] },
  { icon: siUdemy, keywords: ["udemy"] },
  { icon: siMedium, keywords: ["medium"] },
  { icon: siVercel, keywords: ["vercel"] },
  { icon: siNetlify, keywords: ["netlify"] },
  { icon: siDigitalocean, keywords: ["digitalocean", "digital ocean"] },
  { icon: siBackblaze, keywords: ["backblaze"] },
  { icon: siMega, keywords: ["mega"] },
  { icon: siDropbox, keywords: ["dropbox"] },
  { icon: siClaude, keywords: ["claude", "anthropic"] },
  { icon: siDuolingo, keywords: ["duolingo"] },
  { icon: siWise, keywords: ["wise"] },
  { icon: siRevolut, keywords: ["revolut"] },
  { icon: siNordvpn, keywords: ["nordvpn", "nord vpn"] },
  { icon: siProtonvpn, keywords: ["proton vpn", "protonvpn"] },
  { icon: siProton, keywords: ["proton"] },
  { icon: siExpressvpn, keywords: ["expressvpn", "express vpn"] },
  { icon: siZoom, keywords: ["zoom"] },
  { icon: siPatreon, keywords: ["patreon"] },
  { icon: siGoogleplay, keywords: ["google play", "play pass", "play store"] },
  { icon: siGooglecloud, keywords: ["google cloud", "gcp"] },
  { icon: siGoogle, keywords: ["google one", "google", "gemini"] },
  { icon: siApple, keywords: ["apple one", "apple"] },
];

const bySlug = new Map<string, Brand>(ENTRIES.map((e) => [e.icon.slug, e.icon]));

function normalize(s: string): string {
  return s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .trim();
}

/** Best brand match for a free-text subscription name, or null. */
export function matchBrand(name: string): Brand | null {
  const n = normalize(name);
  if (n === "") return null;
  for (const { icon, keywords } of ENTRIES) {
    if (keywords.some((k) => n.includes(k))) return icon;
  }
  return null;
}

export function brandBySlug(slug: string | null): Brand | null {
  return slug ? (bySlug.get(slug) ?? null) : null;
}
