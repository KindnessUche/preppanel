# Image prompts

This landing page currently uses **zero raster images** — every visual is a
recreated app UI (HTML/CSS/SVG, real DOM elements) rather than an
illustration, which is what actually gives Raycast/Linear their "alive"
feeling: those aren't pictures of a product, they're the product's own
interface elements, live.

That said, a few spots would genuinely benefit from a real image if you want
to add one later: an OG/social share image, a favicon/app icon mark, and
potentially a large ambient background texture behind the hero. Prompts for
each are below, written to match the exact visual DNA of your references —
copy these directly into Midjourney, DALL-E, or your generator of choice.

Every prompt below assumes the same base style, stated explicitly each time
so you can mix and match: **dark technical blueprint aesthetic, not
illustration-cute.**

---

## 1. OG / social share image (1200×630)

```
Ultra-minimal dark technical composition on pure near-black background
(#08090a). Center-left: bold sans-serif wordmark "PrepPanel" in off-white
(#f2f3f5), medium weight, tight letter-spacing, roughly 64px equivalent.
Below it, smaller monospace subtext in muted gray (#8b9096): "Interview
practice that pushes back." Center-right: a faint isometric wireframe object
suggesting four overlapping translucent panels/cards fanned out like a hand
of cards, each panel a thin 1px off-white outline at ~15% opacity, one panel
tinted with a soft muted blue glow (#5b8def) at low opacity. A barely-visible
dot grid or blueprint grid pattern in the background at 5% opacity. No
photographic elements, no gradients beyond the single blue glow, no
drop shadows, no rounded skeuomorphic surfaces. Studio-clean, generous
negative space, everything left-aligned to a safe margin. Aspect ratio
1200:630 exactly.
```

## 2. App icon / favicon mark (square, 512×512, needs to read at 16px too)

```
Extremely simple geometric mark on pure black background (#08090a). A single
shape: four thin concentric or overlapping rounded-square outlines (1.5px
stroke, off-white #f2f3f5) representing four panel members, arranged in a
tight 2x2 overlapping cluster like a Venn diagram, each offset slightly so
all four are visible. The topmost/frontmost shape filled solid with muted
blue (#5b8def), the other three left as unfilled outlines. No text, no
gradients, no shadows. Must remain legible as a tiny 16x16px favicon — bold
enough strokes, minimal detail, high contrast. Centered, square canvas, even
padding on all sides.
```

## 3. Hero ambient background texture (2400×1600, used at low opacity behind hero copy)

```
Abstract technical blueprint background, pure black base (#08090a). A faint
isometric grid of thin off-white lines (1px, ~6% opacity) receding toward a
vanishing point in the upper third of the frame, like a technical drawing's
construction lines. Scattered across the grid, a handful of small floating
geometric nodes (circles and thin-outlined rounded rectangles, 1px stroke,
10-15% opacity) at varying isometric depths, as if marking data points on
the grid. One single node near the upper-right glows softly in muted blue
(#5b8def) at 25% opacity, clearly the "active" one among faint peers. No
color anywhere except that one blue accent. No people, no text, no
photographic texture, no lens flare. Must work as a background element sitting
behind foreground text — keep the lower two-thirds of the frame emptier /
lower-contrast than the upper third so text stays readable when overlaid.
Wide aspect ratio, 2400x1600 or similar.
```

## 4. Optional: individual persona "identity marks" (if you want a small glyph per interviewer instead of the current initials-in-circle treatment)

Generate these four as a matched set, same prompt shell, only the [VARIABLE] changes:

```
Minimal abstract geometric avatar mark, single shape on pure black
background (#08090a), no face, no character illustration — purely
geometric/symbolic. Thin outline style, 1.5px stroke, one accent color fill
on a small interior detail only. The shape should evoke [VARIABLE] without
being literal. Circular canvas crop, works at both 200px and 32px sizes.
No gradients, no shadow, no texture.

Variable for "The Closer" (focused, rapid-fire): a tight inward-pointing
triangle/arrow shape, accent color #5b8def, conveying directness and speed.

Variable for "The Advocate" (warm, encouraging): a soft open rounded arc or
partial circle like a gentle embrace shape, accent color #4ade80 (green).

Variable for "The Skeptic" (pressing, devil's advocate): an angular
zigzag or lightning-bolt-adjacent shape, sharp angles, accent color #f87171
(red).

Variable for "The Storyteller" (narrative-first, curious): a spiral or
loose coil shape suggesting a winding path/story arc, accent color #c084fc
(purple).
```

---

## Style DNA reference (for any future prompt you write yourself)

If you want to generate more assets later without referencing this file,
the consistent rules that make everything feel like one coherent product are:

- **Background:** always `#08090a` (near-black), never pure `#000000`
- **Line color:** off-white at low opacity (10–20%), never pure white at 100%
- **The one accent color:** `#5b8def` (muted blue) — used sparingly, one
  element per composition, never more than ~20% of the visual weight
- **No gradients** except the single soft radial glow already used in the
  hero/CTA sections of the live page
- **No drop shadows, no skeuomorphism, no photographic textures**
- **Monospace type only for labels/captions**, sans-serif for headlines —
  if any prompt needs to render text at all, keep it minimal
