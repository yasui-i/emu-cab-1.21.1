"""Generate the shared low-poly balise model and pixel atlas (no cabinet edits)."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/emucab"
SOURCE = ROOT / "model-source/balise"


def main():
    SOURCE.mkdir(parents=True, exist_ok=True)
    (SOURCE / "textures").mkdir(exist_ok=True)
    atlas = Image.new("RGBA", (64, 64), (237, 176, 22, 255))
    draw = ImageDraw.Draw(atlas)
    # Top face: quiet molded yellow, subtle stippling and four recessed screws.
    draw.rectangle((4, 4, 59, 31), fill="#F4C52E")
    draw.line((4, 4, 59, 4), fill="#FFDB57")
    draw.line((4, 4, 4, 31), fill="#FFDB57")
    draw.line((4, 31, 59, 31), fill="#DCA51D")
    draw.line((59, 4, 59, 31), fill="#DCA51D")
    for x in range(8, 58, 4):
        for y in range(8, 30, 4):
            draw.point((x, y), fill="#E4B425")
    for x, y in ((13, 10), (50, 10), (13, 25), (50, 25)):
        draw.rectangle((x - 1, y - 1, x + 1, y + 1), fill="#796B42")
        draw.point((x, y), fill="#CFD1C9")
        draw.point((x, y - 1), fill="#EEEEDE")
    # Two low-key service plugs, shared by both device types.
    for x in (23, 40):
        draw.rectangle((x, 16, x + 1, 17), fill="#B68C21")
    # Side strip: flat mold seam, with a small generic maker plate on the front.
    draw.rectangle((4, 36, 59, 43), fill="#E5AE1F")
    draw.line((4, 36, 59, 36), fill="#F4C541")
    draw.line((4, 43, 59, 43), fill="#C58B13")
    draw.rectangle((23, 39, 32, 41), fill="#4A4B43")
    draw.line((24, 40, 29, 40), fill="#BEBFAE")
    draw.rectangle((35, 39, 39, 41), fill="#9D9F8C")
    draw.point((37, 40), fill="#D5D7C3")
    # Unmarked edges, lid chamfer, charcoal base and metal cable socket.
    draw.rectangle((4, 46, 59, 49), fill="#D69A18")
    draw.rectangle((4, 52, 59, 55), fill="#585D58")
    draw.line((4, 52, 59, 52), fill="#777C72")
    draw.rectangle((4, 58, 19, 61), fill="#92988C")
    draw.line((4, 58, 19, 58), fill="#BEC2B4")
    draw.rectangle((24, 58, 39, 61), fill="#3D433F")
    texture = ASSETS / "textures/block/balise.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(texture)
    atlas.save(SOURCE / "textures/balise.png")
    # UV values in vanilla JSON are expressed on a 16 x 16 atlas coordinate grid.
    def uv(rect):
        return [v / 4 for v in rect]
    top = uv((4, 4, 60, 32))
    side = uv((4, 36, 60, 44))
    edge = uv((4, 46, 60, 50))
    base = uv((4, 52, 60, 56))
    metal = uv((4, 58, 20, 62))
    dark = uv((24, 58, 40, 62))

    def box(name, start, end, surface, upper=None, front=None):
        faces = {face: {"texture": "#body", "uv": surface}
                 for face in ("north", "south", "east", "west", "up", "down")}
        if upper is not None:
            faces["up"]["uv"] = upper
        if front is not None:
            faces["north"]["uv"] = front
        return {"name": name, "from": start, "to": end, "faces": faces}

    elements = [
        box("Gray mounting base", [1.25, 0, 4.75], [14.75, .4, 11.25], base),
        box("Yellow housing", [1, .4, 4.5], [15, 1.35, 11.5], edge, upper=top, front=side),
        box("Inset yellow lid", [1.25, 1.35, 4.75], [14.75, 1.7, 11.25], edge, upper=top),
        box("Rear cable socket", [6.9, .45, 11.5], [9.1, 1.2, 12.1], metal, upper=dark),
    ]
    model = {
        "credit": "EMU Cab railway balise; shared appearance for both functions",
        "ambientocclusion": True,
        "textures": {"body": "emucab:block/balise", "particle": "emucab:block/balise"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 135, 0], "translation": [0, 2.8, 0], "scale": [1.05, 1.05, 1.05]},
            "ground": {"translation": [0, 3, 0], "scale": [.65, .65, .65]},
            "fixed": {"rotation": [90, 0, 0], "scale": [1, 1, 1]},
            "firstperson_righthand": {"rotation": [0, -30, 0], "translation": [0, 4, 0], "scale": [.7, .7, .7]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 3, 0], "scale": [.65, .65, .65]},
        },
    }
    target = ASSETS / "models/block/balise.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    item = ASSETS / "models/item/balise.json"
    item.parent.mkdir(parents=True, exist_ok=True)
    item.write_text(json.dumps({"parent": "emucab:block/balise"}, indent=2) + "\n", encoding="utf-8")
    (SOURCE / "balise-model.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    report = {"cuboids": len(elements), "quads": len(elements) * 6,
              "triangles": len(elements) * 12, "atlas": "64 x 64 pixels",
              "dimensions_pixels": [14, 1.7, 7.6], "long_axis": "local X", "base_y": 0}
    (SOURCE / "model-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
