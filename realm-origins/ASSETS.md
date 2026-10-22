# Recursos visuais — Crónicas RP

## Moedas e notas (0.4.0)

15 ilustrações originais em SVG, convertidas em texturas transparentes de 64 × 64. As moedas usam cobre, dourado ou dois metais; as notas usam cores por denominação, papel e um símbolo arquitetónico de fantasia. Os valores estão desenhados diretamente no asset e também no nome/tooltip do item. Não são fotografias ou cópias de notas reais.

Fontes do desenho: `art/money/*.svg`; gerador opcional: `art/generate_money.py`. Os PNGs e modelos já estão incluídos e não precisas de Python para compilar o mod. Para regenerar apenas a arte: instala CairoSVG e executa `python art/generate_money.py`.

## Fundos do livro e pergaminho (0.2.0)

Texturas criadas com a ferramenta integrada image_gen (modo built-in), usando a skill imagegen. Copiadas para `src/main/resources/assets/cronicasrp/textures/gui/`. A interface, os campos, o modelo 3D, os slots e a assinatura são desenhados pelo código do mod; as imagens não contêm texto ou botões.

Fontes: DejaVu Serif, ficheiro sem alterações, licença em FONT_LICENSE.txt.

## journal.png

Caminho final: `/workspace/scratch/9bf56a1f832f/cronicas-rp/src/main/resources/assets/cronicasrp/textures/gui/journal.png`.

Prompt final:

```text
Use case: stylized-concept.
Asset type: production background texture for an actual Minecraft medieval fantasy RPG character journal GUI, landscape 3:2.
Primary request: A magnificently crafted ancient open book, straight overhead, completely flat orthographic view, symmetrical two facing blank parchment pages, dark oxblood leather cover, subtle embossed antiqued brass corners, many layered page edges, visible thin central binding seam. Wide landscape open book nearly fills the canvas; its two pages are equal width and leave broad pale blank usable writing areas. Hand-painted high-end fantasy game UI, warm ivory parchment with very subtle fibers and softly darker worn edges, exquisitely restrained gold engraved filigree confined to the tiny outer corners. Dramatic craftsmanship, readable calm interiors. Leather extends only slightly beyond the parchment. No oblique perspective. No lettering or symbols on the blank pages. No UI controls. No page folds or clutter in the writing areas. No characters, hands, objects, backdrop, watermark or words. Real alpha transparent outside the silhouette of the book; preserve tiny shadows and irregular page edges.
```

## ritual.png

Caminho final: `/workspace/scratch/9bf56a1f832f/cronicas-rp/src/main/resources/assets/cronicasrp/textures/gui/ritual.png`.

Prompt final:

```text
Use case: stylized-concept.
Asset type: blank landscape 3:2 mystical character-creation parchment background for an actual Minecraft fantasy RPG GUI.
Primary request: A mysterious ancient dark scroll laid perfectly flat in straight orthographic overhead view. It occupies nearly the whole rectangular canvas, with very thin rolled parchment ends at the top and bottom, ragged softly worn edges, exquisitely subtle antique brass corner flourishes, engraved tiny star and crescent ornaments restricted to the outer edge. The entire interior is blank, matte midnight indigo and desaturated charcoal blue parchment, even and calm, suitable for ivory text and form controls to be placed later. Dark aged paper, very subtle fibers, faint dusty blue shimmer around outer edges only, beautifully detailed hand-painted premium medieval fantasy game interface. The blank central area is at least 88% of the image. Landscape silhouette ratio 3:2, no diagonal perspective, no deep shadows in interior. No text, no letters, no buttons, no portraits, no skeletons, no hands, no objects placed on the page, no large runes, no watermark, no decorative objects blocking the center. True transparent alpha outside the scroll silhouette.
```

