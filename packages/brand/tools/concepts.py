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
        # One continuous silhouette avoids seams where brush strokes overlap.
        # Broad, gently rising shoulders and a rounded taper keep the initial
        # handwritten without making the stem look like a sharp nib.
        return [
            "M40.5,36.5"
            "C40.5,32.6 42.2,30.7 46,29.7"
            "C56,27.1 65.8,26.6 73.3,30.5"
            "C74,30.9 74,31.4 73.2,31.7"
            "C66.4,34.4 58.4,35.5 50.1,37.4"
            "L49.7,50.8"
            "C55.8,49.3 61.8,49.6 66.7,52.7"
            "C67.4,53.1 67.4,53.6 66.6,53.9"
            "C61.6,56 55.4,57.5 49.3,58.9"
            "C48.8,66.5 47.5,73.1 45.1,78.8"
            "C44.7,79.8 44,79.7 43.7,78.6"
            "C41.4,68.3 40.5,55.1 40.5,36.5Z",
            circle_path(74, 54, 3.2),
        ]
    if name == "C":
        return [stroke_path(*STEM), stroke_path(*TOP_CURL), stroke_path(*MID)]
    raise KeyError(name)


CONCEPTS = {
    "A": dict(title="A  Ink on paper", bg=CREAM, fg=INK),
    "B": dict(title="B  Refined signature F on sage", bg=SAGE, fg=WHITE),
    "C": dict(title="C  Page-curl flourish", bg=SAGE_SOFT, fg=SAGE),
}


def svg(name, background=True, fg=None, size=108):
    c = CONCEPTS[name]
    body = "".join(f'<path d="{d}"/>' for d in mark_paths(name))
    bg = f'<rect width="108" height="108" fill="{c["bg"]}"/>' if background else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
            f'viewBox="0 0 108 108">{bg}<g fill="{fg or c["fg"]}">{body}</g></svg>\n')
