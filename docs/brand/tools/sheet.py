"""Render concept SVGs with rsvg-convert and compose the comparison sheet."""
import os, subprocess, sys
from PIL import Image, ImageDraw, ImageFont
from concepts import CONCEPTS, svg

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
SHEET = sys.argv[2] if len(sys.argv) > 2 else os.path.join(OUT, "sheet.png")
os.makedirs(OUT, exist_ok=True)


def render(svg_text, px, path):
    src = path + ".svg"
    open(src, "w").write(svg_text)
    subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), src, "-o", path], check=True)
    return Image.open(path).convert("RGBA")


def mask(size, kind, ss=4):
    S = size * ss
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    if kind == "circle":
        d.ellipse([0, 0, S - 1, S - 1], fill=255)
    else:  # squircle, superellipse n=4
        import math
        pts = []
        for i in range(720):
            a = 2 * math.pi * i / 720
            c, s = math.cos(a), math.sin(a)
            x = abs(c) ** 0.5 * (1 if c >= 0 else -1)
            y = abs(s) ** 0.5 * (1 if s >= 0 else -1)
            pts.append((S / 2 + x * S / 2, S / 2 + y * S / 2))
        d.polygon(pts, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def launcher(name, size, kind, mono=False):
    """Adaptive icon as a launcher shows it: 108 grid, visible 72 centre, masked."""
    full = size * 108 // 72
    if mono:
        # Android 13 themed icon: foreground alpha tinted, on a tonal background.
        fg = render(svg(name, background=False, fg="#1F3B33"), full, f"{OUT}/{name}_mono_{full}.png")
        img = Image.new("RGBA", fg.size, "#CFE6DC")
        img.alpha_composite(fg)
    else:
        img = render(svg(name), full, f"{OUT}/{name}_full_{full}.png")
    off = (full - size) // 2
    img = img.crop((off, off, off + size, off + size))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask(size, kind))
    return out


def font(px):
    for f in [os.path.expanduser("~/Library/Fonts/Geist-Medium.otf"),
              "/System/Library/Fonts/Helvetica.ttc"]:
        if os.path.exists(f):
            return ImageFont.truetype(f, px)
    return ImageFont.load_default()


def panel(name, bg, label_col):
    W, H = 600, 512
    p = Image.new("RGBA", (W, H), bg)
    d = ImageDraw.Draw(p)
    f = font(18)
    p.alpha_composite(launcher(name, 180, "circle"), (40, 40))
    p.alpha_composite(launcher(name, 180, "squircle"), (250, 40))
    p.alpha_composite(launcher(name, 120, "circle", mono=True), (450, 70))
    d.text((40, 232), "circle 180", fill=label_col, font=f)
    d.text((250, 232), "squircle 180", fill=label_col, font=f)
    d.text((450, 232), "themed", fill=label_col, font=f)
    x = 40
    for kind in ("circle", "squircle"):
        p.alpha_composite(launcher(name, 48, kind), (x, 300)); x += 72
    p.alpha_composite(launcher(name, 48, "circle", mono=True), (x, 300)); x += 72
    # a fake row of neighbouring launcher icons at 48 px for context
    d.text((40, 362), "48 px (circle, squircle, themed)", fill=label_col, font=f)
    for i, col in enumerate(["#E5533D", "#3B7DDD", "#F2B233", "#7B5CC4"]):
        cx = 40 + i * 72
        d.ellipse([cx, 410, cx + 48, 458], fill=col)
    p.alpha_composite(launcher(name, 48, "circle"), (40 + 4 * 72, 410))
    return p


rows = []
for name, c in CONCEPTS.items():
    big = render(svg(name), 512, f"{OUT}/{name}_512.png")
    row = Image.new("RGBA", (512 + 600 * 2 + 80, 512 + 70), "#FAFAF8")
    d = ImageDraw.Draw(row)
    d.text((20, 18), c["title"], fill="#222", font=font(28))
    row.alpha_composite(big, (20, 60))
    row.alpha_composite(panel(name, "#EDEAE4", "#555"), (552, 60))
    row.alpha_composite(panel(name, "#1B1D22", "#AAA"), (552 + 620, 60))
    rows.append(row)
sheet = Image.new("RGB", (rows[0].width, sum(r.height for r in rows) + 20), "#FAFAF8")
y = 10
for r in rows:
    sheet.paste(r, (0, y), r); y += r.height
sheet.save(SHEET)
print(SHEET)
