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

# D — Layers: nested squares tucked toward the top-right; depth = layered keys.
t, g = 24, 12
x, y, s, rr = 20.0, 20.0, 216.0, 44.0
d = ""
for i in range(2):
    d += rrect(x, y, s, s, rr) + " " + rrect(x+t, y+t, s-2*t, s-2*t, rr-t*0.55) + " "
    ns = (s - 2*t) * 0.66
    x, y = x + s - t - g - ns, y + t + g   # hug top-right of the hole
    s, rr = ns, max(rr*0.66, 12)
d += rrect(x, y, s, s, rr)
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

# G — Fold: a square folded shut along its diagonal; the flap tucks inside, nothing loose.
s0, r0, gp = 208, 36, 14; a = (256 - s0)/2; b = a + s0
lower = (f"M{a:.2f} {a+r0:.2f} V{b-r0:.2f} A{r0} {r0} 0 0 0 {a+r0:.2f} {b:.2f} H{b-r0:.2f} "
         f"A{r0} {r0} 0 0 0 {b:.2f} {b-r0:.2f} Z")
dxl, dyl = s0, s0 - 2*r0; L = math.hypot(dxl, dyl)
nx, ny = dyl/L*gp, -dxl/L*gp        # normal pointing up-right
p1 = (a + nx, a + r0 + ny); p2 = (b + nx, b - r0 + ny)
# intersect offset line with top edge y=a and right edge x=b
ta = (a - p1[1]) / (dyl/ L) / L * L if False else (a - p1[1]) / (dyl/dxl) if dyl else 0
top_x = p1[0] + (a - p1[1]) * dxl / dyl
right_y = p1[1] + (b - p1[0]) * dyl / dxl
flap = (f"M{top_x:.2f} {a:.2f} H{b-r0:.2f} A{r0} {r0} 0 0 1 {b:.2f} {a+r0:.2f} V{right_y:.2f} Z")
svg(lower + " " + flap, "g-fold.svg", "nonzero")
