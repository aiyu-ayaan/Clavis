# Final mark: Sleeve. A card rises out of its sleeve, with a thumb notch to pull it out.
# The same geometry feeds the Android vectors: ui-common/.../drawable/clavis_glyph.xml and
# app/.../drawable/splash_mark_animated.xml. Change it here, then copy the printed paths.
import math
def pt(cx, cy, r, a): return (cx + r*math.cos(math.radians(a)), cy + r*math.sin(math.radians(a)))
def f(p): return f"{p[0]:.2f},{p[1]:.2f}"
def rrect(x, y, w, h, r):
    return (f"M{x+r},{y} H{x+w-r} A{r},{r} 0 0 1 {x+w},{y+r} V{y+h-r} A{r},{r} 0 0 1 {x+w-r},{y+h} "
            f"H{x+r} A{r},{r} 0 0 1 {x},{y+h-r} V{y+r} A{r},{r} 0 0 1 {x+r},{y} Z")
# Grid (256): card x52..204 y26..104 (top corners r16), chip 30x24 r5 at (72,44).
# Sleeve x28..228 y116..230, top corners r10, bottom r22, thumb notch r28 at (128,116).
CARD = "M52,104 V42 A16,16 0 0 1 68,26 H188 A16,16 0 0 1 204,42 V104 Z"
CHIP = rrect(72, 44, 30, 24, 5)
SLEEVE = ("M38,116 H100 A28,28 0 0 0 156,116 H218 A10,10 0 0 1 228,126 V208 A22,22 0 0 1 206,230 "
          "H50 A22,22 0 0 1 28,208 V126 A10,10 0 0 1 38,116 Z")
if __name__ == "__main__":
    glyph = " ".join([CARD, CHIP, SLEEVE])
    open("clavis-mark.svg", "w").write('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256"><title>Clavis</title>'
        f'<path fill="#000" fill-rule="evenodd" d="{glyph}"/></svg>\n')
    for k in ("CARD", "CHIP", "SLEEVE"): print(k, "=", globals()[k])
