"""Folio icon concepts: stroke geometry in the 108x108 adaptive-icon grid."""
from brush import stroke_path

CREAM = "#F6F1E6"
INK = "#1E2B28"
SAGE = "#2F7A68"
SAGE_SOFT = "#E3EFEA"
WHITE = "#FFFFFF"

STEM = ([((46.4, 34), (46.9, 50), (46.7, 66), (45.4, 80.5))],
        dict(w_start=9.4, w_peak=9.6, w_body=7.3, entry=0.12, tail=0.46, tip=0.7, power=1.5))
TOP = ([((46.4, 34.0), (54, 31.2), (64, 29.8), (75, 30.2))],
       dict(w_start=9.4, w_peak=9.4, w_body=6.2, entry=0.10, tail=0.5, tip=0.6, power=1.4))
MID = ([((47.8, 56), (54.5, 53.8), (61, 53.2), (68.5, 53.8))],
       dict(w_start=8.4, w_peak=8.4, w_body=5.4, entry=0.12, tail=0.55, tip=0.6, power=1.4))

# Concept C: the top stroke runs out and curls back like a page corner lifting.
TOP_CURL = ([((46.4, 34), (55, 32.3), (63, 30.3), (70, 29.6)),
             ((70, 29.6), (78, 28.6), (81.5, 33.5), (79, 38)),
             ((79, 38), (77, 41.5), (72.5, 42), (70.8, 39.2))],
            dict(w_start=9.4, w_peak=9.4, w_body=5.4, entry=0.08, tail=0.62, tip=0.8, power=1.1))


def circle_path(cx, cy, r):
    return (f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0Z")


def mark_paths(name):
    if name == "A":
        return [stroke_path(*STEM), stroke_path(*TOP), stroke_path(*MID)]
    if name == "B":
        return [stroke_path(*STEM), stroke_path(*TOP), stroke_path(*MID),
                circle_path(74.8, 52.6, 3)]
    if name == "C":
        return [stroke_path(*STEM), stroke_path(*TOP_CURL), stroke_path(*MID)]
    raise KeyError(name)


CONCEPTS = {
    "A": dict(title="A  Ink on paper", bg=CREAM, fg=INK),
    "B": dict(title="B  White brush on sage, ink dot", bg=SAGE, fg=WHITE),
    "C": dict(title="C  Page-curl flourish", bg=SAGE_SOFT, fg=SAGE),
}


def svg(name, background=True, fg=None, size=108):
    c = CONCEPTS[name]
    body = "".join(f'<path d="{d}"/>' for d in mark_paths(name))
    bg = f'<rect width="108" height="108" fill="{c["bg"]}"/>' if background else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
            f'viewBox="0 0 108 108">{bg}<g fill="{fg or c["fg"]}">{body}</g></svg>\n')
