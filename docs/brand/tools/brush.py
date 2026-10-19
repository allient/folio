"""Brush-stroke outline generator for the Folio F mark.

Each stroke is a centreline (one or more cubic Bezier segments) plus a width
profile. The outline is sampled along both sides, closed with a round entry cap
and a pointed tail, and smoothed back into cubic Beziers (Catmull-Rom), so the
result is a plain filled path usable in SVG and Android VectorDrawable alike.
"""
import math


def bez(p0, p1, p2, p3, t):
    u = 1 - t
    return (u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1])


def sample_centerline(segs, n):
    """segs: list of (p0,p1,p2,p3). Returns n+1 points evenly spaced by arc length."""
    dense = []
    for s in segs:
        for i in range(200):
            dense.append(bez(*s, i / 200))
    dense.append(segs[-1][3])
    acc = [0.0]
    for a, b in zip(dense, dense[1:]):
        acc.append(acc[-1] + math.dist(a, b))
    total = acc[-1]
    out, j = [], 0
    for k in range(n + 1):
        target = total * k / n
        while j < len(acc) - 2 and acc[j + 1] < target:
            j += 1
        seg = acc[j + 1] - acc[j] or 1
        f = (target - acc[j]) / seg
        a, b = dense[j], dense[j + 1]
        out.append((a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
    return out


def smoothstep(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def profile(t, w_start, w_peak, w_body, entry=0.12, tail=0.35, tip=0.35, power=1.6):
    """Width along the stroke: press in, slight thinning through the body, taper out.

    The body width falls linearly from w_peak (at t=entry) to w_body (at t=1) and
    the tail multiplies it down to the tip, so the outline has no kinks.
    """
    base = w_peak + (w_body - w_peak) * max(0.0, (t - entry) / (1 - entry))
    if t < entry:
        base = w_start + (w_peak - w_start) * smoothstep(t / entry)
    tail_start = 1 - tail
    if t <= tail_start:
        return base
    f = min(1.0, (t - tail_start) / tail)
    return tip + (base - tip) * math.cos(math.pi / 2 * f) ** (0.6 * power)


def stroke_outline(segs, widths, n=48, cap_steps=7):
    pts = sample_centerline(segs, n)
    left, right = [], []
    for i, p in enumerate(pts):
        a = pts[max(0, i - 1)]
        b = pts[min(len(pts) - 1, i + 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        w = profile(i / n, **widths) / 2
        left.append((p[0] + nx * w, p[1] + ny * w))
        right.append((p[0] - nx * w, p[1] - ny * w))
    # round entry cap around the first point, going from right[0] back to left[0]
    p0, p1 = pts[0], pts[1]
    ang = math.atan2(p1[1] - p0[1], p1[0] - p0[0])
    r = profile(0, **widths) / 2
    cap = []
    for k in range(1, cap_steps):
        a = ang - math.pi / 2 - math.pi * k / cap_steps
        cap.append((p0[0] + r * math.cos(a), p0[1] + r * math.sin(a)))
    # thin the outline points for a cleaner path (keep ends dense)
    def thin(side):
        keep = side[0:len(side) - 1:2] + [side[-1]]
        return keep
    L, R = thin(left), thin(right)
    # small round lift-off at the tail, from left[-1] round to right[-1]
    pe, pb = pts[-1], pts[-2]
    ang_e = math.atan2(pe[1] - pb[1], pe[0] - pb[0])
    re_ = profile(1, **widths) / 2
    tail_cap = [(pe[0] + re_ * math.cos(ang_e + math.pi / 2 - math.pi * k / 4),
                 pe[1] + re_ * math.sin(ang_e + math.pi / 2 - math.pi * k / 4)) for k in range(1, 4)]
    ring = L[:-1] + tail_cap + list(reversed(R))[1:] + cap
    # drop the duplicated tip neighbours so the tail stays pointed
    return ring


def ring_to_path(ring, sharp_index=None):
    """Closed Catmull-Rom spline through ring -> SVG path data."""
    n = len(ring)
    f = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")
    d = [f"M{f(ring[0][0])},{f(ring[0][1])}"]
    for i in range(n):
        p0, p1, p2, p3 = ring[i - 1], ring[i], ring[(i + 1) % n], ring[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        if sharp_index is not None:
            if i == sharp_index:
                c1 = p1
            if (i + 1) % n == sharp_index:
                c2 = p2
        d.append(f"C{f(c1[0])},{f(c1[1])} {f(c2[0])},{f(c2[1])} {f(p2[0])},{f(p2[1])}")
    d.append("Z")
    return "".join(d)


def stroke_path(segs, widths, **kw):
    ring = stroke_outline(segs, widths, **kw)
    # tip index = index of the centreline end point inside ring
    n_left = len(ring) - (len(ring) - 1) // 2  # not exact; find by tip match
    tip = segs[-1][3]
    idx = min(range(len(ring)), key=lambda i: math.dist(ring[i], tip))
    return ring_to_path(ring)


def translate(segs, dx, dy):
    return [tuple((x + dx, y + dy) for (x, y) in s) for s in segs]
