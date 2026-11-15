# Generates Clavis logo concepts as pure filled paths (256x256, black).
import math
C = 128.0
def pt(cx, cy, r, a):  # a in degrees, 0 = right, positive = clockwise (SVG y-down)
    return (cx + r*math.cos(math.radians(a)), cy + r*math.sin(math.radians(a)))
def f(p): return f"{p[0]:.2f} {p[1]:.2f}"
def csector(cx, cy, rm, t, a0, a1):
    """Annular sector with round caps, from a0 to a1 clockwise (a1 > a0)."""
    ro, ri, rc = rm + t/2, rm - t/2, t/2
    large = 1 if (a1 - a0) > 180 else 0
    return (f"M{f(pt(cx,cy,ro,a0))} A{ro} {ro} 0 {large} 1 {f(pt(cx,cy,ro,a1))} "
            f"A{rc} {rc} 0 0 1 {f(pt(cx,cy,ri,a1))} "
            f"A{ri} {ri} 0 {large} 0 {f(pt(cx,cy,ri,a0))} "
            f"A{rc} {rc} 0 0 1 {f(pt(cx,cy,ro,a0))} Z")
def circle(cx, cy, r, ccw=False):
    s = 0 if ccw else 1
    return f"M{cx-r:.2f} {cy:.2f} A{r} {r} 0 1 {s} {cx+r:.2f} {cy:.2f} A{r} {r} 0 1 {s} {cx-r:.2f} {cy:.2f} Z"
def svg(d, name, rule="nonzero"):
    open(name, "w").write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">'
                          f'<path fill="#000" fill-rule="{rule}" d="{d}"/></svg>\n')

# A — Inward: outer C opens right, inner C opens left, solid core. Equal stroke and gaps.
t, g = 30, 18
r_out = 112 - t/2
r_in = r_out - t - g
core = r_in - t/2 - g
gapA = 38  # half-opening angle of the outer C, degrees
d = csector(C, C, r_out, t, gapA, 360 - gapA)          # opening right
d += " " + csector(C, C, r_in, t, 180 + 48, 180 + 360 - 48)  # opening left
d += " " + circle(C, C, core)
svg(d, "a-inward.svg")

# B — Seal: 9-lobe scalloped disc with a C cut through it (evenodd).
n, Rc, rl = 9, 86.0, 34.0
lobes = [pt(C, C, Rc, -90 + i*360/n) for i in range(n)]
def isect(p, q, r):  # outer intersection of two equal circles
    mx, my = (p[0]+q[0])/2, (p[1]+q[1])/2
    dd = math.dist(p, q)/2; h = math.sqrt(r*r - dd*dd)
    ux, uy = (mx - C), (my - C); L = math.hypot(ux, uy)
    return (mx + ux/L*h, my + uy/L*h)
pts = [isect(lobes[i], lobes[(i+1) % n], rl) for i in range(n)]
ds = f"M{f(pts[-1])} " + " ".join(f"A{rl} {rl} 0 0 1 {f(p)}" for p in pts) + " Z"
ds += " " + csector(C, C, 50, 28, 45, 315)
svg(ds, "b-seal.svg", "evenodd")

# C — Held: a bowl (lower half annulus, round caps) cradling a solid core.
tb = 32; rb = 92
dc = csector(C, C + 14, rb, tb, 0, 180)
dc += " " + circle(C, C + 14 - 8, 46)
svg(dc, "c-held.svg")
