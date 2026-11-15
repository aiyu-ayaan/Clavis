# Round 2: non-letter concepts (no C, no key, no card). 256x256, black fills.
import math
C = 128.0
def f(p): return f"{p[0]:.2f} {p[1]:.2f}"
def svg(d, name, rule="evenodd"):
    open(name, "w").write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">'
                          f'<path fill="#000" fill-rule="{rule}" d="{d}"/></svg>\n')
def rrect(x, y, w, h, r):
    return (f"M{x+r:.2f} {y:.2f} H{x+w-r:.2f} A{r} {r} 0 0 1 {x+w:.2f} {y+r:.2f} V{y+h-r:.2f} "
            f"A{r} {r} 0 0 1 {x+w-r:.2f} {y+h:.2f} H{x+r:.2f} A{r} {r} 0 0 1 {x:.2f} {y+h-r:.2f} "
            f"V{y+r:.2f} A{r} {r} 0 0 1 {x+r:.2f} {y:.2f} Z")
def circle(cx, cy, r):
    return f"M{cx-r:.2f} {cy:.2f} A{r} {r} 0 1 1 {cx+r:.2f} {cy:.2f} A{r} {r} 0 1 1 {cx-r:.2f} {cy:.2f} Z"
def inset(poly, g):
    """Offset a convex polygon (clockwise in SVG coords) inward by g."""
    n = len(poly); lines = []
    for i in range(n):
        (x1, y1), (x2, y2) = poly[i], poly[(i+1) % n]
        dx, dy = x2-x1, y2-y1; L = math.hypot(dx, dy)
        nx, ny = -dy/L, dx/L  # inward normal for clockwise order in y-down coords
        lines.append(((x1+nx*g, y1+ny*g), (dx, dy)))
    out = []
    for i in range(n):
        (p, d1), (q, d2) = lines[i-1], lines[i]
        den = d1[0]*d2[1] - d1[1]*d2[0]
        t = ((q[0]-p[0])*d2[1] - (q[1]-p[1])*d2[0]) / den
        out.append((p[0]+d1[0]*t, p[1]+d1[1]*t))
    return out
def poly(pts): return "M" + " L".join(f(p) for p in pts) + " Z"

# D — Layers: nested squares tucked into the top-right corner; depth = layered keys.
# Explicit grid: outer ring t24, middle ring t20 (gap 14), solid core (gap 10).
d = rrect(20, 20, 216, 216, 30) + " " + rrect(44, 44, 168, 168, 14)
d += " " + rrect(90, 58, 108, 108, 20) + " " + rrect(110, 78, 68, 68, 8)
d += " " + rrect(132, 88, 36, 36, 8)
svg(d, "d-layers.svg")

# E — Facet: an isometric block, three faces split by gaps; top face hollow = nothing hidden from you.
R = 112
v = [(C + R*math.cos(math.radians(-90+60*k)), C + R*math.sin(math.radians(-90+60*k))) for k in range(6)]
gap = 9
top = inset([v[5], v[0], v[1], (C, C)], gap)
right = inset([(C, C), v[1], v[2], v[3]], gap)
left = inset([v[5], (C, C), v[3], v[4]], gap)
svg(poly(top) + " " + poly(right) + " " + poly(left), "e-facet.svg", "nonzero")

# F — Arch: a doorway with a dot inside: a place your things live, with one door you open.
w, th = 176, 30; x0 = C - w/2; top_y, bot = 22, 234; ro = w/2; ri = ro - th
da = (f"M{x0:.2f} {bot} V{top_y+ro:.2f} A{ro} {ro} 0 0 1 {x0+w:.2f} {top_y+ro:.2f} V{bot} "
      f"H{x0+w-th:.2f} V{top_y+ro:.2f} A{ri} {ri} 0 0 0 {x0+th:.2f} {top_y+ro:.2f} V{bot} Z")
da += " " + circle(C, 200, 26)
svg(da, "f-arch.svg", "nonzero")

# G — Fold: a square folded shut on its 45-degree diagonal; the flap tucks in, nothing loose.
s0, r0, gp = 208, 36, 12; a = (256 - s0)/2; b = a + s0; k = gp*math.sqrt(2)
lower = f"M{a:.2f} {a:.2f} L{b:.2f} {b:.2f} H{a+r0:.2f} A{r0} {r0} 0 0 1 {a:.2f} {b-r0:.2f} Z"
flap = f"M{a+k:.2f} {a:.2f} H{b-r0:.2f} A{r0} {r0} 0 0 1 {b:.2f} {a+r0:.2f} V{b-k:.2f} Z"
svg(lower + " " + flap, "g-fold.svg", "nonzero")
