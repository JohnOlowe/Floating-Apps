#!/usr/bin/env python3
"""
Build an Instagram carousel summarising the Sunday School lesson on Marriage.

Output: 1080 x 1350 (4:5 portrait) JPEGs, slide-01 .. slide-08, in ./slides/
"""

import os
import random
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))
BG = os.path.join(HERE, "bg")
OUT = os.path.join(HERE, "slides")
os.makedirs(OUT, exist_ok=True)

W, H = 1080, 1350
M = 96  # side margin

# ------------------------------------------------------------------ palette
CREAM = (247, 242, 233)
INK = (28, 26, 24)
MAROON = (122, 31, 43)
MUTED = (122, 112, 100)
GOLD = (190, 155, 92)
LIGHT = (250, 246, 239)

F = "/usr/local/lib/python3.11/dist-packages/matplotlib/mpl-data/fonts/ttf/"
SERIF = F + "STIXGeneral.ttf"
SERIF_B = F + "STIXGeneralBol.ttf"
SERIF_I = F + "STIXGeneralItalic.ttf"
SERIF_BI = F + "STIXGeneralBolIta.ttf"
SANS = F + "DejaVuSans.ttf"
SANS_B = F + "DejaVuSans-Bold.ttf"


def font(path, size):
    return ImageFont.truetype(path, size)


# ------------------------------------------------------------------ helpers
def paper(w=W, h=H, seed=0, base=CREAM):
    """Procedural warm paper texture — clean enough to hold body text."""
    rnd = random.Random(seed)
    img = Image.new("RGB", (w, h), base)
    # fine grain
    noise = Image.effect_noise((w, h), 26).convert("L")
    noise = noise.filter(ImageFilter.GaussianBlur(0.6))
    img = Image.composite(
        Image.new("RGB", (w, h), tuple(min(255, c + 7) for c in base)),
        img, noise.point(lambda v: 255 if v > 150 else 0))
    grain = Image.effect_noise((w, h), 8).convert("L").point(lambda v: 128 + (v - 128) // 5)
    img = Image.blend(img, Image.merge("RGB", (grain, grain, grain)), 0.05)
    # soft blotches for handmade feel
    blot = Image.new("L", (w, h), 0)
    bd = ImageDraw.Draw(blot)
    for _ in range(16):
        x, y = rnd.randint(0, w), rnd.randint(0, h)
        r = rnd.randint(180, 460)
        bd.ellipse([x - r, y - r, x + r, y + r], fill=rnd.randint(6, 18))
    blot = blot.filter(ImageFilter.GaussianBlur(120))
    img = Image.composite(Image.new("RGB", (w, h), (238, 230, 216)), img, blot)
    # vignette
    vig = Image.new("L", (w, h), 0)
    ImageDraw.Draw(vig).ellipse([-w * 0.34, -h * 0.26, w * 1.34, h * 1.26], fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(200))
    img = Image.composite(img, Image.new("RGB", (w, h), (226, 216, 200)), vig)
    return img


def photo(name, darken=0.0, blur=0.0, warm=1.0, sat=1.0):
    """Load a background photo, cover-crop to 1080x1350, and grade it."""
    im = Image.open(os.path.join(BG, name)).convert("RGB")
    sc = max(W / im.width, H / im.height)
    im = im.resize((round(im.width * sc), round(im.height * sc)), Image.LANCZOS)
    im = im.crop(((im.width - W) // 2, (im.height - H) // 2,
                  (im.width - W) // 2 + W, (im.height - H) // 2 + H))
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur))
    if sat != 1.0:
        im = ImageEnhance.Color(im).enhance(sat)
    if warm != 1.0:
        r, g, b = im.split()
        r = r.point(lambda v: min(255, int(v * warm)))
        b = b.point(lambda v: int(v / warm))
        im = Image.merge("RGB", (r, g, b))
    if darken:
        im = Image.blend(im, Image.new("RGB", (W, H), (0, 0, 0)), darken)
    return im


def scrim(img, top=0.0, bottom=0.0, colour=(0, 0, 0), full=0.0):
    """Gradient scrim so text always has contrast."""
    ov = Image.new("L", (W, H), 0)
    px = ov.load()
    for y in range(H):
        t = y / H
        a = full * 255
        if top:
            a = max(a, top * 255 * max(0.0, 1 - t / 0.55) ** 1.4)
        if bottom:
            a = max(a, bottom * 255 * max(0.0, (t - 0.42) / 0.58) ** 1.3)
        v = int(a)
        for x in range(W):
            px[x, y] = v
    return Image.composite(Image.new("RGB", (W, H), colour), img, ov)


def wrap(draw, text, fnt, maxw):
    words, lines, cur = text.split(), [], ""
    for wd in words:
        t = (cur + " " + wd).strip()
        if draw.textlength(t, font=fnt) <= maxw or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = wd
    if cur:
        lines.append(cur)
    return lines


def block(draw, x, y, text, fnt, fill, maxw, leading=1.34, align="left",
          shadow=None):
    """Draw wrapped text; returns the y after the block."""
    lines = text.split("\n") if "\n" in text else wrap(draw, text, fnt, maxw)
    lh = round(fnt.size * leading)
    for ln in lines:
        lw = draw.textlength(ln, font=fnt)
        cx = x + (maxw - lw) / 2 if align == "center" else x
        if shadow:
            draw.text((cx + shadow[0], y + shadow[1]), ln, font=fnt, fill=shadow[2])
        draw.text((cx, y), ln, font=fnt, fill=fill)
        y += lh
    return y


def tracked(draw, x, y, text, fnt, fill, track=7, align="left", maxw=None):
    """Letter-spaced label (small caps style kicker)."""
    total = sum(draw.textlength(c, font=fnt) + track for c in text) - track
    if align == "center" and maxw:
        x = x + (maxw - total) / 2
    for c in text:
        draw.text((x, y), c, font=fnt, fill=fill)
        x += draw.textlength(c, font=fnt) + track
    return total


def rule(draw, x, y, w, colour=GOLD, th=2):
    draw.rectangle([x, y, x + w, y + th - 1], fill=colour)


def badge(draw, n, total, colour=MUTED):
    """Small 'n / total' marker, bottom right."""
    f = font(SANS, 22)
    t = f"{n:02d} / {total:02d}"
    tw = draw.textlength(t, font=f)
    draw.text((W - M - tw, H - 74), t, font=f, fill=colour)


def brand(draw, colour=MUTED, text="SUNDAY SCHOOL"):
    tracked(draw, M, H - 72, text, font(SANS, 19), colour, track=5)


def save(img, n, name):
    p = os.path.join(OUT, f"slide-{n:02d}-{name}.jpg")
    img.save(p, "JPEG", quality=94, subsampling=0, optimize=True)
    print("  ", os.path.basename(p))


TOTAL = 8
CW = W - 2 * M  # content width

# ================================================================== slide 1
im = photo("cover.jpg", darken=0.30, warm=1.03, sat=0.88)
im = scrim(im, full=0.30, top=0.52, bottom=0.86, colour=(30, 18, 14))
d = ImageDraw.Draw(im)
tracked(d, M, 250, "SUNDAY SCHOOL", font(SANS, 24), (226, 205, 178), track=11)
y = 318
f1 = font(SERIF_B, 150)
d.text((M, y), "Marriage", font=f1, fill=(252, 248, 242))
y += 190
rule(d, M, y, 130, GOLD, 3)
y += 52
block(d, M, y, "What God instituted\nin the beginning",
      font(SERIF_I, 54), (238, 224, 206), CW, leading=1.3)
y = H - 300
block(d, M, y, "Seven things Genesis says about the man,\nthe woman, and why she was brought to him.",
      font(SANS, 27), (214, 199, 182), CW, leading=1.5)
tracked(d, M, H - 150, "SWIPE", font(SANS, 21), GOLD, track=8)
d.text((M + 108, H - 152), "\u2192", font=font(SANS, 24), fill=GOLD)
save(im, 1, "cover")

# ================================================================== slide 2
im = paper(seed=2)
d = ImageDraw.Draw(im)
tracked(d, M, 132, "GENESIS 1:26-28", font(SANS, 21), MAROON, track=7)
rule(d, M, 178, 64, GOLD, 3)
y = 250
y = block(d, M, y, "She is not\nan afterthought", font(SERIF_B, 86), INK, CW, leading=1.16)
y += 46
y = block(d, M, y,
          "God did not plan the man and then improvise the "
          "woman. Scripture says it was male and female He "
          "set out to create.", font(SERIF, 40), (58, 54, 50), CW, leading=1.5)
y += 30
y = block(d, M, y,
          "He blessed them. He said to them: be fruitful, "
          "multiply, fill the earth and subdue it.",
          font(SERIF, 40), (58, 54, 50), CW, leading=1.5)
y += 44
rule(d, M, y, CW, (222, 210, 192), 1)
y += 36
block(d, M, y, "Them. Them. Them.\nThe mandate was never given to one.",
      font(SERIF_BI, 38), MAROON, CW, leading=1.42)
brand(d); badge(d, 2, TOTAL)
save(im, 2, "not-an-afterthought")

# ================================================================== slide 3
im = paper(seed=3)
d = ImageDraw.Draw(im)
tracked(d, M, 132, "GENESIS 2:18", font(SANS, 21), MAROON, track=7)
rule(d, M, 178, 64, GOLD, 3)
y = 250
y = block(d, M, y, "\u201cIt is not good\u201d\ndoes not mean\n\u201cevil\u201d", font(SERIF_B, 82), INK, CW, leading=1.16)
y += 44
y = block(d, M, y,
          "Sin had not yet entered the world. Six times God "
          "had already said: it is good.",
          font(SERIF, 39), (58, 54, 50), CW, leading=1.5)
y += 28
y = block(d, M, y,
          "So when He says it is not good for the man to be "
          "alone, He is not calling him evil. He is saying "
          "the man alone is incomplete \u2014 unable, by himself, "
          "to carry the purpose he was given.",
          font(SERIF, 39), (58, 54, 50), CW, leading=1.5)
brand(d); badge(d, 3, TOTAL)
save(im, 3, "not-good")

# ================================================================== slide 4
im = paper(seed=4)
d = ImageDraw.Draw(im)
tracked(d, M, 132, "ALONE \u2260 LONELY", font(SANS, 21), MAROON, track=7)
rule(d, M, 178, 64, GOLD, 3)
y = 244
y = block(d, M, y, "Adam was\nalone. He was\nnot lonely.", font(SERIF_B, 84), INK, CW, leading=1.16)
y += 46
y = block(d, M, y,
          "He had the presence of God and every living "
          "creature. He knew no deficiency, no ache, no "
          "unmet need.",
          font(SERIF, 39), (58, 54, 50), CW, leading=1.5)
y += 30
y = block(d, M, y,
          "God does not hand you a counterpart because you "
          "feel lonely. He does it when you have been formed, "
          "placed, and are walking in His command.",
          font(SERIF, 39), (58, 54, 50), CW, leading=1.5)
y += 42
rule(d, M, y, CW, (222, 210, 192), 1)
y += 34
block(d, M, y, "Other people pairing up is not your signal.",
      font(SERIF_BI, 37), MAROON, CW, leading=1.4)
brand(d); badge(d, 4, TOTAL)
save(im, 4, "alone-not-lonely")

# ================================================================== slide 5
im = paper(seed=5)
d = ImageDraw.Draw(im)
tracked(d, M, 132, "GENESIS 2:19-20", font(SANS, 21), MAROON, track=7)
rule(d, M, 178, 64, GOLD, 3)
y = 250
y = block(d, M, y, "God created\nthe desire", font(SERIF_B, 88), INK, CW, leading=1.16)
y += 46
y = block(d, M, y,
          "Naming the animals was an act of authority and "
          "intellect \u2014 Adam defining the nature of every "
          "creature.",
          font(SERIF, 39), (58, 54, 50), CW, leading=1.5)
y += 28
y = block(d, M, y,
          "Creature after creature, he saw the pairs and "
          "concluded: not of my kind. That is where the "
          "longing was born.",
          font(SERIF, 39), (58, 54, 50), CW, leading=1.5)
y += 28
y = block(d, M, y,
          "The delay was not failure. It taught him his own "
          "limit \u2014 so that when she came, he received her "
          "with gratitude, not entitlement.",
          font(SERIF, 39), (58, 54, 50), CW, leading=1.5)
brand(d); badge(d, 5, TOTAL)
save(im, 5, "the-desire")

# ================================================================== slide 6
im = photo("rib.jpg", darken=0.32, warm=1.02, sat=0.9)
im = scrim(im, full=0.18, top=0.42, colour=(14, 10, 12))
d = ImageDraw.Draw(im)
tracked(d, M, 138, "GENESIS 2:18  \u00b7  PSALM 121:1-2", font(SANS, 21), (206, 170, 120), track=6)
rule(d, M, 184, 64, GOLD, 3)
y = 252
y = block(d, M, y, "\u201cHelper\u201d is not\n\u201csubordinate\u201d",
          font(SERIF_B, 80), (250, 246, 240), CW, leading=1.18)
y += 50
f = font(SERIF_BI, 92)
d.text((M, y), "ezer", font=f, fill=GOLD)
y += 132
y = block(d, M, y,
          "The same Hebrew word Scripture uses for the woman, "
          "it uses for God Himself: \u201cMy help comes from the "
          "LORD.\u201d",
          font(SERIF, 38), (226, 214, 200), CW, leading=1.52)
y += 26
block(d, M, y,
      "Indispensable, life-saving strength. A sustainer. "
      "Not a lesser rank \u2014 a comparable counterpart.",
      font(SERIF, 38), (226, 214, 200), CW, leading=1.52)
brand(d, (170, 155, 138)); badge(d, 6, TOTAL, (170, 155, 138))
save(im, 6, "ezer")

# ================================================================== slide 7
im = paper(seed=7, base=(243, 236, 226))
d = ImageDraw.Draw(im)
tracked(d, M, 132, "GENESIS 2:21-22", font(SANS, 21), MAROON, track=7)
rule(d, M, 178, 64, GOLD, 3)
y = 244
y = block(d, M, y, "She was brought\nto him", font(SERIF_B, 84), INK, CW, leading=1.16)
y += 40
y = block(d, M, y,
          "Adam never went searching. God brought her \u2014 so no "
          "man could claim a hand in her making.",
          font(SERIF, 37), (58, 54, 50), CW, leading=1.48)
y += 46
# quote panel
qx, qy = M + 8, y
qf = font(SERIF_I, 40)
qlines = ["Not from his head, to rule over him,",
          "nor from his foot, to be trampled on,",
          "but out of his side, to be his equal;",
          "under his arm, to be protected;",
          "near his heart, to be loved."]
qh = round(qf.size * 1.52) * len(qlines)
d.rectangle([qx, qy, qx + 4, qy + qh - 12], fill=MAROON)
ty = qy
for ln in qlines:
    d.text((qx + 34, ty), ln, font=qf, fill=MAROON)
    ty += round(qf.size * 1.52)
y = qy + qh + 34
block(d, M, y, "She is your strength. Love her like it.",
      font(SERIF_B, 39), INK, CW, leading=1.4)
brand(d); badge(d, 7, TOTAL)
save(im, 7, "the-rib")

# ================================================================== slide 8
im = photo("closing.jpg", darken=0.30, warm=1.02, sat=0.95)
im = scrim(im, top=0.34, bottom=0.72, colour=(34, 22, 14))
d = ImageDraw.Draw(im)
tracked(d, M, 150, "GENESIS 2:23-24", font(SANS, 21), (216, 184, 136), track=7)
rule(d, M, 196, 64, GOLD, 3)
y = 268
y = block(d, M, y, "\u201cThis is now\nbone of my\nbones\u201d",
          font(SERIF_B, 82), (252, 249, 244), CW, leading=1.18)
y += 48
y = block(d, M, y,
          "The man was formed, placed, and given a task \u2014 then "
          "given her. Gratitude made him prophesy: this is "
          "co-man, a co-labourer in God\u2019s purpose.",
          font(SERIF, 38), (232, 220, 206), CW, leading=1.5)
y = H - 392
rule(d, M, y, CW, (150, 120, 90), 1)
y += 44
block(d, M, y, "Marriage is not defined by what\nthe world says, but by what\nScripture has said.",
      font(SERIF_BI, 44), (250, 240, 226), CW, leading=1.34)
tracked(d, M, H - 92, "SAVE  \u00b7  SHARE  \u00b7  FULL NOTES IN BIO",
        font(SANS, 20), (208, 178, 132), track=5)
save(im, 8, "closing")

print("\nDone \u2014 8 slides in", OUT)
