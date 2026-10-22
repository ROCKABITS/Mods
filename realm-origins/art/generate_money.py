"""Original currency art. Optional regeneration: python -m pip install cairosvg; python art/generate_money.py."""
from pathlib import Path
import json
import cairosvg

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/cronicasrp"
SOURCE = ROOT / "art/money"
SOURCE.mkdir(parents=True, exist_ok=True)
(ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
(ASSETS / "models/item").mkdir(parents=True, exist_ok=True)

DENOMINATIONS = [
    ("coin_1_cent", 1, "1 cêntimo"), ("coin_2_cents", 2, "2 cêntimos"),
    ("coin_5_cents", 5, "5 cêntimos"), ("coin_10_cents", 10, "10 cêntimos"),
    ("coin_20_cents", 20, "20 cêntimos"), ("coin_50_cents", 50, "50 cêntimos"),
    ("coin_1_euro", 100, "1 euro"), ("coin_2_euros", 200, "2 euros"),
    ("note_5_euros", 500, "5 euros"), ("note_10_euros", 1000, "10 euros"),
    ("note_20_euros", 2000, "20 euros"), ("note_50_euros", 5000, "50 euros"),
    ("note_100_euros", 10000, "100 euros"), ("note_200_euros", 20000, "200 euros"),
    ("note_500_euros", 50000, "500 euros"),
]
NOTE_COLORS = ["#929892", "#b4564e", "#507fa0", "#bf8449", "#639365", "#b5a354", "#8a619f"]

for index, (identifier, cents, label) in enumerate(DENOMINATIONS):
    number = str(cents if cents < 100 else cents // 100)
    if cents < 500:
        copper = cents < 10
        outer = "#a85832" if copper else "#c7a551"
        inner = "#e8a26a" if copper else "#f4d580"
        if cents == 100:
            outer, inner = "#d3ad52", "#d4d9d3"
        elif cents == 200:
            outer, inner = "#bac4c0", "#e6c776"
        svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
<defs><linearGradient id="metal" x1="0" y1="0" x2="1" y2="1"><stop stop-color="{inner}"/><stop offset="1" stop-color="{outer}"/></linearGradient></defs>
<ellipse cx="33" cy="35" rx="27" ry="26" fill="#291e17" opacity=".45"/>
<circle cx="31" cy="30" r="27" fill="{outer}" stroke="#5d4530" stroke-width="2"/>
<circle cx="31" cy="30" r="23" fill="none" stroke="#fff0bd" stroke-width="1" opacity=".7" stroke-dasharray="2 3"/>
<circle cx="31" cy="30" r="20" fill="url(#metal)" stroke="#7f673f" stroke-width="1.5"/>
<path d="M15 18 Q31 7 47 18" fill="none" stroke="#fff4d7" opacity=".65"/>
<text x="31" y="37" text-anchor="middle" fill="#65462e" font-family="DejaVu Serif" font-weight="bold" font-size="25">{number}</text>
<text x="31" y="47" text-anchor="middle" fill="#75593b" font-family="DejaVu Serif" font-size="8">{'CENT' if cents < 100 else 'EURO'}</text>
<path d="M9 30l3-2 2 2-2 2zm39 0l3-2 2 2-2 2z" fill="#fff0bc"/>
</svg>'''
    else:
        color = NOTE_COLORS[index - 8]
        size = 15 if len(number) == 3 else 22 if len(number) == 2 else 24
        svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
<defs><linearGradient id="paper" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#efe2bd"/><stop offset="1" stop-color="#d7c9a3"/></linearGradient></defs>
<rect x="3" y="16" width="60" height="37" rx="2" fill="#231e20" opacity=".35"/>
<rect x="1" y="12" width="61" height="37" rx="2" fill="url(#paper)" stroke="#544c3c" stroke-width="1.5"/>
<rect x="4" y="15" width="55" height="31" fill="none" stroke="{color}" stroke-width="2"/>
<rect x="5" y="16" width="54" height="5" fill="{color}" opacity=".9"/>
<path d="M5 40Q17 19 32 40T59 36M5 36Q18 17 32 36T59 33" fill="none" stroke="{color}" opacity=".35"/>
<path d="M7 28l10-6 10 6v13H7zm3-1v-6h4v4m9 2v-6h-4v4" fill="{color}" opacity=".5"/>
<path d="M13 41v-8a4 4 0 018 0v8" fill="#eee0b9" stroke="{color}" stroke-width="1.2"/>
<rect x="30" y="22" width="2" height="23" fill="{color}" opacity=".5"/>
<text x="45" y="39" text-anchor="middle" fill="{color}" font-family="DejaVu Serif" font-weight="bold" font-size="{size}">{number}</text>
<text x="45" y="45" text-anchor="middle" fill="#625847" font-family="DejaVu Serif" font-size="5">EURO</text>
<text x="8" y="20" fill="#f5eacc" font-family="DejaVu Serif" font-size="4.3">CRÓNICAS</text>
<path d="M55 15l2 3-2 3-2-3z" fill="#f5eacc"/>
</svg>'''
    (SOURCE / f"{identifier}.svg").write_text(svg, encoding="utf-8")
    cairosvg.svg2png(bytestring=svg.encode(), write_to=str(ASSETS / f"textures/item/{identifier}.png"))
    (ASSETS / f"models/item/{identifier}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"cronicasrp:item/{identifier}"}}, indent=2), encoding="utf-8")

for locale in ("pt_pt", "pt_br", "en_us"):
    path = ASSETS / f"lang/{locale}.json"
    language = json.loads(path.read_text(encoding="utf-8"))
    for identifier, cents, label in DENOMINATIONS:
        name = ("Nota de " if cents >= 500 else "Moeda de ") + label
        if locale == "en_us":
            number = cents // 100 if cents >= 100 else cents
            name = f"{number} " + (("Euro Banknote" if cents >= 500 else "Euro Coin") if cents >= 100 else "Cent Coin")
        language[f"item.cronicasrp.{identifier}"] = name
    language.update({
        "itemGroup.cronicasrp.economy": "Crónicas · Economia" if locale != "en_us" else "Chronicles · Currency",
        "tooltip.cronicasrp.cash.value": "Valor: %s" if locale != "en_us" else "Value: %s",
        "tooltip.cronicasrp.cash.stack": "Valor da pilha: %s" if locale != "en_us" else "Stack value: %s",
        "tooltip.cronicasrp.cash.use": "Botão direito: depositar uma unidade." if locale != "en_us" else "Right-click: deposit one item.",
        "tooltip.cronicasrp.cash.shift": "Shift + botão direito: depositar a pilha." if locale != "en_us" else "Shift + right-click: deposit the stack.",
    })
    path.write_text(json.dumps(language, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
for name, predicate in [("money", lambda c: True), ("coins", lambda c: c < 500), ("banknotes", lambda c: c >= 500)]:
    path = ROOT / f"src/main/resources/data/cronicasrp/tags/item/{name}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"replace": False, "values": [f"cronicasrp:{identifier}" for identifier, cents, label in DENOMINATIONS if predicate(cents)]}, indent=2) + "\n", encoding="utf-8")
print("15 texturas, modelos, traduções e tags gerados.")
