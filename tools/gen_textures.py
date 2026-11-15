import os
from pathlib import Path
from PIL import Image

root = str(Path(__file__).resolve().parents[1] / 'src/main/resources/assets/emucab/textures/block')
os.makedirs(root, exist_ok=True)


def brushed(base, spread, seam=True):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            n = ((x * 13 + y * 7) % spread) - spread // 2
            v = base + n
            px[x, y] = (max(0, v - 3), max(0, v), min(255, v + 5), 255)
    if seam:
        for y in (0, 15):
            for x in range(16):
                px[x, y] = (max(0, base - 14), max(0, base - 10), max(0, base - 6), 255)
    return img


brushed(54, 7).save(os.path.join(root, "base_dark.png"))
brushed(176, 9, seam=False).save(os.path.join(root, "binnacle_placeholder.png"))
brushed(68, 6).save(os.path.join(root, "binnacle.png"))


def desk():
    img = brushed(182, 11, seam=False)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if y >= 13:
                v = 150 - (y - 13) * 6
                px[x, y] = (v - 4, v, v + 4, 255)
    for x in range(0, 16, 8):
        for y in range(16):
            r, g, b, a = px[x, y]
            px[x, y] = (r - 10, g - 10, b - 10, a)
    # Original CR400AF dark blue-grey composite, with restrained pixel variation.
    for y in range(16):
        for x in range(16):
            n = ((x//2*3+y//2*5)%5)*2
            px[x,y] = (52+n, 66+n, 78+n, 255)
    img.save(os.path.join(root, "desk_light.png"))


def screen():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = 9 + (2 if y % 2 else 0)
            edge = 6 if (x in (0, 15) or y in (0, 15)) else 0
            px[x, y] = (v - 3, v - 1 + edge // 2, v + 5 + edge, 255)
    img.save(os.path.join(root, "screen.png"))


def keypad():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = (32, 36, 41, 255)
    for ky in range(1, 16, 3):
        for kx in range(1, 16, 3):
            for y in range(ky, min(ky + 2, 16)):
                for x in range(kx, min(kx + 2, 16)):
                    px[x, y] = (66, 74, 84, 255)
    img.save(os.path.join(root, "panel_keypad.png"))


def button(name, color):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 7.0:
                px[x, y] = (24, 26, 30, 255)
            elif d > 6.0:
                px[x, y] = tuple(min(255, c + 40) for c in color) + (255,)
            else:
                px[x, y] = color + (255,)
    img.save(os.path.join(root, name + ".png"))


desk()
screen()
keypad()
button("btn_red", (176, 46, 40))
button("btn_green", (44, 160, 84))
button("btn_yellow", (222, 176, 44))
button("btn_blue", (44, 104, 178))
button("btn_white", (214, 218, 222))

brushed(134, 5).save(os.path.join(root, 'metal.png'))
vent = brushed(48, 5)
for y in (4, 6, 8, 10, 12):
    for x in range(3, 13):
        vent.putpixel((x,y), (17,22,28,255))
vent.save(os.path.join(root, 'vent.png'))

os.remove(os.path.join(root, "binnacle_placeholder.png"))
print("textures:", sorted(os.listdir(root)))
