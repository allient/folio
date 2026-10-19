# Folio — Guía de identidad visual

**Versión:** 1.0 · Octubre de 2026  
**Producto:** Handwritten Notes & eReader

## 1. Concepto

**La escritura se encuentra con la lectura.** El símbolo «F.» conserva el gesto caligráfico de Folio y fusiona la página que se dobla. La F fluida comunica escritura espontánea, el punto es la marca del lápiz y la esquina doblada representa un libro que se transforma en un cuaderno personal.

## 2. Personalidad

Serena, humana, enfocada, cálida, inteligente, táctil y contemporánea. Evitar la estética infantil, el exceso de detalles y el tecnicismo visual.

## 3. Sistema de logotipos

- **Principal** (`svg/folio-icon-primary.svg`): F verde sobre papel crema y pliegue inferior; apropiado para web, portada, prensa y tienda.
- **Oscuro** (`svg/folio-icon-dark.svg`): para fondos oscuros y piezas de alto contraste.
- **Plano** (`svg/folio-icon-flat.svg`): fondo verde y F blanca; pantallas pequeñas, launcher y contextos donde el pliegue no es legible.
- **Solo símbolo** (`svg/folio-symbol-*`): monocolor para grabado, impresión, favicons y marcas de agua.
- **Combinaciones** (`svg/folio-lockup-*.svg`): icono, nombre y subtítulo, en vertical y horizontal.

## 4. Paleta

| Color | HEX | Uso |
|---|---|---|
| Sage · Primario | `#2F7A68` | F, botones, elementos de marca |
| Ink · Texto | `#1E2B28` | Titulares y texto |
| Cream · Papel | `#F6F1E6` | Superficies cálidas, fondo principal |
| Sage soft | `#E3EFEA` | Fondos ligeros, pliegue |
| Blanco | `#FFFFFF` | Fondos neutros y símbolo invertido |
| Fold mint | `#C7E3D8` | Detalle de pliegue en la versión ilustrada |

Las variantes oscuras mantienen contraste legible. Verificar WCAG en interfaces: los colores de marca no sustituyen una comprobación de contraste para texto pequeño.

## 5. Tipografía

**Geist** es la opción preferente para la palabra «Folio» (Medium / SemiBold), y **Inter** es alternativa de interfaz. Usar títulos semibold y textos regulares con espaciado generoso. Los SVG de las combinaciones usan texto editable y requieren una tipografía instalada o la sustitución por la fuente fallback; convertir a trazados antes de distribuir archivos cerrados. No se incluyen archivos de fuentes.

## 6. Área de seguridad y tamaños

- Mantener al menos **12 % del ancho del icono** libre alrededor del símbolo cuando se use sin contenedor.
- Icono mínimo recomendado: **32 px** en formato plano; **64 px** para apreciar el pliegue ilustrado.
- Evitar que otros elementos toquen las curvas o el punto de la F.
- Android: recursos adaptativos 108 dp, con marca visual centrada y versión monocromática.

## 7. Usos correctos

Usar colores originales; conservar proporciones; recurrir a la versión plana si el fondo o el tamaño hacen confuso el pliegue; preferir PNG 512 px full bleed para la tienda. Usar SVG para interfaces y documentos vectoriales.

## 8. Usos incorrectos

No estirar, rotar la F, separar el punto, incorporar sombras fuertes adicionales, alterar el verde principal, añadir degradados arbitrarios ni añadir un segundo icono de libro o lápiz encima. No utilizar la versión crema sobre fondos claros sin borde o separación suficiente.

## 9. Archivos y producción

- `svg/`: originales vectoriales editables.
- `png/`: iconos de 48, 96, 192, 512 y 1024 px; variantes de la marca; pieza para Play Store.
- `android/res/`: VectorDrawables y adaptive launcher icons listos para copiar a `app/src/main/res/`.
- `docs/`: guía PDF y Markdown.
- `preview/`: lámina de aplicaciones.

### Implementación Android

Copiar `android/res/` al módulo Android y confirmar en el manifiesto `android:icon="@mipmap/ic_launcher"` y `android:roundIcon="@mipmap/ic_launcher_round"`. Probar máscaras circular y cuadrada, modo oscuro, tema monocromo Android 13+, y resolución de 48 px en dispositivo. La versión de launcher es plana para garantizar reconocimiento.

## 10. Nota de fidelidad

La marca vectorial es una **reconstrucción interpretativa** del concepto visual aprobado a partir de una imagen rasterizada generada. Respeta el trazo fluido y el esquema de color, pero no es un calco geométricamente idéntico píxel a píxel; es editable y puede perfeccionarse si se necesita replicación exacta.
