---
title: Play Console answers
description: Copy-ready store listing text and the answers for every Play Console form.
---

Everything to paste into Play Console, in one place. The listing text comes from `apps/mobile/fastlane/metadata/android/`. Those files are the source of truth: if you change the text, edit them, then update this page or run `just play-metadata`.

For the step-by-step process, see [Publish on Google Play](google-play.md).

## Store listing: English (en-US)

Grow users → Store presence → **Main store listing**.

**App name** (25/30)

```text
Folio: Notes & PDF Reader
```

**Short description** (80/80)

```text
Handwritten notes, infinite canvas and a PDF & EPUB reader. Offline, no account.
```

**Full description** (2051/4000). Paste it with the `<b>` tags; Google Play shows them as bold headings.

```text
Folio is a calm, focused notebook for your stylus. Write by hand, sketch on an infinite canvas and read PDFs and EPUBs with a pen in hand. Your notes and books stay on your device: no account, no ads, and everything works offline.

<b>Ink that behaves like ink</b>
• Twelve brushes: ballpoint, fineliner, gel pen, marker, fountain pen, brush pen, calligraphy, highlighter, 2H–6B pencils, chalk, spray and a bristle paint brush
• Low-latency drawing with pressure and tilt
• Pressure curves you shape by hand, plus a threshold that ignores a worn pen tip
• Pencil grain that stays on the paper as you zoom

<b>Pages, or no edges at all</b>
• 35 page templates: lined, grid, dots, Cornell, planner, Kanban, music staff, habit tracker and more
• Infinite canvas with pins, a minimap and "back to content"
• Layers, lasso, shapes, fill, text boxes, tables, stickers, sticky notes and images
• A ruler and a zoom window for detail work
• Voice notes inside the page

<b>Read with a pen in hand</b>
• PDF and EPUB reader with highlights, underlines, margin notes and bookmarks
• Search, jump to page and on-device text recognition (OCR)
• Study view: a book and a notebook side by side
• Export the annotated PDF, or your reading notes as Markdown
• Open books straight from your file manager or other apps

<b>A library that stays tidy</b>
• Folders with colours and styles, favourites, recent and search across names, typed text, tags and book titles
• Trash with automatic clean-up after 7 or 30 days
• Smart backups to a folder you choose, with daily, weekly and monthly versions
• Export pages as PDF or PNG, or a notebook as an editable .folio file

<b>Private by design</b>
Folio works fully offline. Your notes and books never leave your device unless you export or share them yourself. Folio shares anonymous usage statistics (which screens and features are used) to improve the app; you can turn this off in Preferences. The microphone is used only when you record a voice note.

Made for Android tablets and pen displays, and works on phones too.
```

## Store listing: Spanish (es-419)

Main store listing → **Manage translations → Add your own translations → Spanish (Latin America)**.

**App name** (25/30)

```text
Folio: Notas y lector PDF
```

**Short description** (80/80)

```text
Notas a mano, lienzo infinito y lector de PDF y EPUB. Sin conexión y sin cuenta.
```

**Full description** (2197/4000)

```text
Folio es un cuaderno tranquilo y enfocado para tu lápiz óptico. Escribe a mano, dibuja en un lienzo infinito y lee PDF y EPUB con el lápiz en la mano. Tus notas y libros se quedan en tu dispositivo: sin cuenta, sin anuncios y todo funciona sin conexión.

<b>Tinta que se comporta como tinta</b>
• Doce pinceles: bolígrafo, rotulador fino, gel, marcador, pluma estilográfica, pincel, caligrafía, resaltador, lápices 2H–6B, tiza, aerosol y pincel de cerdas
• Trazo de baja latencia con presión e inclinación
• Curvas de presión ajustables y un umbral que ignora una punta gastada
• Grano de lápiz que se mantiene en el papel al hacer zoom

<b>Páginas, o sin bordes</b>
• 35 plantillas: rayado, cuadrícula, puntos, Cornell, agenda, Kanban, pentagrama, seguimiento de hábitos y más
• Lienzo infinito con marcadores, minimapa y "volver al contenido"
• Capas, lazo, formas, relleno, cuadros de texto, tablas, stickers, notas adhesivas e imágenes
• Regla y ventana de zoom para el detalle
• Notas de voz dentro de la página

<b>Lee con el lápiz en la mano</b>
• Lector de PDF y EPUB con resaltados, subrayados, notas al margen y marcadores
• Búsqueda, ir a página y reconocimiento de texto (OCR) en el dispositivo
• Vista de estudio: un libro y un cuaderno lado a lado
• Exporta el PDF anotado o tus notas de lectura en Markdown
• Abre libros directamente desde tu gestor de archivos u otras apps

<b>Una biblioteca ordenada</b>
• Carpetas con colores y estilos, favoritos, recientes y búsqueda por nombre, texto, etiquetas y títulos de libros
• Papelera con limpieza automática a los 7 o 30 días
• Copias de seguridad inteligentes en la carpeta que elijas, con versiones diarias, semanales y mensuales
• Exporta páginas como PDF o PNG, o un cuaderno como archivo .folio editable

<b>Privado por diseño</b>
Folio funciona sin conexión. Tus notas y libros nunca salen de tu dispositivo a menos que tú los exportes o compartas. Folio comparte estadísticas de uso anónimas (qué pantallas y funciones se usan) para mejorar la app; puedes desactivarlo en Preferencias. El micrófono solo se usa cuando grabas una nota de voz.

Hecho para tabletas Android y pantallas con lápiz, y también funciona en teléfonos.
```

## Graphics

All files are in `apps/mobile/fastlane/metadata/android/en-US/images/`. The Spanish listing reuses them.

| Field | File | Size |
|---|---|---|
| App icon | `icon.png` | 512×512 |
| Feature graphic | `featureGraphic.png` | 1024×500 |
| Phone screenshots | `phoneScreenshots/1_library.png` … `4_canvas.png` (4) | 1080×1920 |
| 7-inch tablet screenshots | `sevenInchScreenshots/` (7) | 1440×2560 |
| 10-inch tablet screenshots | `tenInchScreenshots/` (7) | 1440×2560 |

## Release notes

For each release. Keep them under 500 characters.

English:

```text
First release of Folio: handwritten notes, infinite canvas, PDF and EPUB reader, smart backups.
```

Spanish:

```text
Primera versión de Folio: notas a mano, lienzo infinito, lector de PDF y EPUB y copias de seguridad inteligentes.
```

## Store settings

Grow users → Store presence → **Store settings**.

| Field | Value |
|---|---|
| App or game | App |
| Category | **Productivity** |
| Tags (up to 5) | Note taking · Drawing (or Art & design) · PDF / Document viewer · E-book reader · Productivity (or Education) |
| Email | Your public support email |
| Website | Your docs site URL |
| Privacy policy | `https://<your-docs-domain>/privacy/` |

Don't use tags for things Folio doesn't do (audio recorder, audiobook, handwriting recognition).

## App content

### Privacy policy

`https://<your-docs-domain>/privacy/`

### Ads

**No, my app does not contain ads.**

### App access

**All functionality in my app is available without any access restrictions.**

### Content rating

IARC questionnaire. Category: **Utility, Productivity, Communication, or Other**. Answer **No** to violence, sexual content, profanity, controlled substances, gambling, user-to-user interaction, sharing location and digital purchases. Expected rating: Everyone / PEGI 3.

### Target audience

**13 and over** (13–15, 16–17, 18 and over). Don't select under 13: that brings Families policy requirements.

### News app

**No.**

### Advertising ID

**Does your app use advertising ID? → No.** The `AD_ID` permission is removed in the manifest and ad-ID collection is off in Firebase.

### Government apps, financial features, health

Not a government app. No financial features. No health features.

### Data safety

**Step 2: Data collection and security**

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Account creation | **My app does not allow users to create an account** |
| Do you provide a way for users to request that their data is deleted? | **Yes** (contact in the privacy policy; collection can also be turned off in Preferences) |
| Additional badges | Skip |

**Step 3: Data types.** Tick only these four:

| Category | Data type |
|---|---|
| App activity | ☑ App interactions |
| App info and performance | ☑ Crash logs, ☑ Diagnostics |
| Device or other IDs | ☑ Device or other IDs |

Leave everything else unticked, including **Audio files → Voice or sound recordings**: voice notes stay on the device.

**Step 4: Data usage and handling.** The same answers for each of the four types:

| Question | Answer |
|---|---|
| Collected, shared, or both? | ☑ **Collected** only (Firebase is a service provider, not sharing) |
| Processed ephemerally? | **No** |
| Required or optional? | **Users can choose whether this data is collected** |
| Why is this data collected? | **Analytics**. Add **App functionality** for Crash logs and Diagnostics |

**Step 5: Preview.** It must show: data collected (App activity, App info and performance, Device or other IDs), no data shared, encrypted in transit, deletion can be requested.

## Keep it in sync

If the app starts collecting something new, or stops, update in this order:
1. `apps/docs/src/pages/privacy.astro`
2. The Data safety form, using this page
3. The store description (`fastlane/metadata/android/*/full_description.txt`)
4. This page
