"""Regenerate Just Share's store and Android icons from one geometric mark."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2]
NAVY = "#102238"
ACCENT = "#E1414F"
WHITE = "#FFFFFF"
SCALE = 4


def icon(size: int, *, background: bool = True, round_icon: bool = False,
         mark: bool = True, monochrome: bool = False) -> Image.Image:
    image = Image.new("RGBA", (512 * SCALE, 512 * SCALE), NAVY if background else (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    def rect(box, radius, fill):
        draw.rounded_rectangle(tuple(round(value * SCALE) for value in box),
                               radius=radius * SCALE, fill=fill)

    def triangle(points, fill):
        draw.polygon([(round(x * SCALE), round(y * SCALE)) for x, y in points], fill=fill)

    if mark:
        rect((100, 164, 335, 218), 27, WHITE)
        triangle(((300, 126), (390, 191), (300, 256)), WHITE)
        accent = WHITE if monochrome else ACCENT
        rect((177, 294, 412, 348), 27, accent)
        triangle(((212, 256), (122, 321), (212, 386)), accent)

    if round_icon:
        mask = Image.new("L", image.size)
        ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
        image.putalpha(mask)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def save(image: Image.Image, path: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target, format="PNG", optimize=True)


save(icon(512), "store-assets/brand/app-icon-512.png")
save(icon(2048), "app/src/main/res/drawable/app_logo_full.png")
for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    folder = f"app/src/main/res/mipmap-{density}"
    save(icon(size), f"{folder}/ic_launcher.png")
    save(icon(size, round_icon=True), f"{folder}/ic_launcher_round.png")
    save(icon(size, background=False), f"{folder}/ic_launcher_foreground.png")
    save(icon(size, mark=False), f"{folder}/ic_launcher_background.png")
    save(icon(size, background=False, monochrome=True), f"{folder}/ic_launcher_monochrome.png")


graphic = Image.new("RGB", (1024, 500), NAVY)
graphic.paste(icon(240).convert("RGB"), (64, 130))
draw = ImageDraw.Draw(graphic)
font = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
regular = "/System/Library/Fonts/Supplemental/Arial.ttf"
draw.text((350, 182), "Just Share", font=ImageFont.truetype(font, 65), fill=WHITE)
draw.text((354, 275), "Files to the other phone. Simply.",
          font=ImageFont.truetype(regular, 32), fill="#B8D7E7")
save(graphic, "store-assets/exports/android/feature-graphic/1024x500/en/01-feature-graphic.png")
