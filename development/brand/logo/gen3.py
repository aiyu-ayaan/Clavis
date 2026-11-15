# Round 3: card-based concepts, each with one ownable twist. 256x256, black fills.
def svg(d, name, rule="evenodd"):
    open(name, "w").write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">'
                          f'<path fill="#000" fill-rule="{rule}" d="{d}"/></svg>\n')
def rrect(x, y, w, h, r):
    return (f"M{x+r} {y} H{x+w-r} A{r} {r} 0 0 1 {x+w} {y+r} V{y+h-r} A{r} {r} 0 0 1 {x+w-r} {y+h} "
            f"H{x+r} A{r} {r} 0 0 1 {x} {y+h-r} V{y+r} A{r} {r} 0 0 1 {x+r} {y} Z")
def circle(cx, cy, r):
    return f"M{cx-r} {cy} A{r} {r} 0 1 1 {cx+r} {cy} A{r} {r} 0 1 1 {cx-r} {cy} Z"

# H — Masked: a card that shows only dots and one group, as the app does by default.
# Only the mask shows: four large dots, no number, no chip.
d = rrect(28, 65, 200, 126, 22) + " " + " ".join(circle(x, 128, 13) for x in (74, 110, 146, 182))
svg(d, "h-masked.svg")

# I — Sleeve: a card tucked into its sleeve, thumb notch to pull it out.
card = "M52 104 V42 A16 16 0 0 1 68 26 H188 A16 16 0 0 1 204 42 V104 Z " + rrect(72, 44, 30, 24, 5)
sleeve = ("M38 116 H100 A28 28 0 0 0 156 116 H218 A10 10 0 0 1 228 126 V208 A22 22 0 0 1 206 230 "
          "H50 A22 22 0 0 1 28 208 V126 A10 10 0 0 1 38 116 Z")
svg(card + " " + sleeve, "i-sleeve.svg")

# J — Home card: a card with an arched doorway and something resting inside.
d = rrect(28, 65, 200, 126, 20) + " M98 171 V117 A30 30 0 0 1 158 117 V171 Z"
svg(d, "j-home.svg")

# K — Original & copy: solid card in front, outlined card behind it. Copies are temporary.
front = rrect(24, 88, 176, 111, 18) + " " + rrect(44, 110, 30, 24, 5)
back = ("M56 76 V74 A18 18 0 0 1 74 56 H214 A18 18 0 0 1 232 74 V149 A18 18 0 0 1 214 167 H212 V153 "
        "H214 A4 4 0 0 0 218 149 V74 A4 4 0 0 0 214 70 H74 A4 4 0 0 0 70 74 V76 Z")
svg(front + " " + back, "k-copy.svg")
