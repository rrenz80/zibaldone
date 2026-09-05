# Zibaldone — Rapporto di progetto

**Stato al 2026-09-05:** v1.9 (build 10), compilato, consegnato come
`Zibaldone-v1.9.apk` (unico installabile in `~/`).
Risorsa da build: `app/build/outputs/apk/debug/app-debug.apk`.

> **Nome.** Fino alla v1.8 l'app si chiamava *Moodboard App*
> (`com.moodboard.app`). Dalla v1.9 è **Zibaldone** (`it.zibaldone.app`):
> lo zibaldone è il quaderno in cui si accumulano appunti, ritagli e
> pensieri sparsi senza un ordine — un moodboard di carta. I riferimenti
> al vecchio nome nelle sezioni storiche qui sotto sono lasciati
> intatti: descrivono cosa è successo allora.

---

## 1. Overview

**Zibaldone** è un'app Android per la creazione di moodboard su un
**piano infinito** (whiteboard) con tre attrezzi espliciti —
**Seleziona · Penna · Gomma** — più note di testo e foto:

- disegno libero (tratti vettoriali, spessore regolabile 2–24 dp)
- **gomma parziale** con raggio regolabile (12–64 dp) e anteprima a cerchio:
  erode solo la porzione di tratto toccata
- note di testo **selezionabili, spostabili, ridimensionabili (8–160 sp),
  modificabili solo a doppio tap**
- foto importate dalla galleria, **selezionabili, spostabili,
  ridimensionabili (0.25×–6×)**
- **pan/zoom ancorato** a 2 dita (0.1×–10×) + pan a 1 dito su spazio vuoto
- **Elimina** per togliere l'elemento attualmente selezionato (tratto,
  nota o foto)
- **Centra** (fit-to-content) e **Azzera** (pan 0, zoom 1) come rete di
  sicurezza
- **export/import portabile** in un singolo file `.zib` (ZIP + manifest +
  media)

La UI è tutta **Jetpack Compose + Material 3**; il dato è un modello
serializzabile puro (`kotlinx.serialization`), nessun database locale
(Room 2.6.1 è dichiarata nel gradle ma non usata).

## 2. Funzionalità (stato v1.9)

| Area | Dettaglio |
|---|---|
| Toolbar bassa | **Seleziona** · **Penna** (slider "Spessore penna" 2–24 dp) · **Gomma** (slider "Dimensione gomma" 12–64 dp). In modalità Seleziona il pannello mostra hint contestuali, diversi a seconda che non ci sia selezione, che sia selezionata una nota, una foto, o che una foto sia in rotazione |
| FAB | **Foto** (pick SAF `image/*` al centro dell'**area board**) · **Nota** (crea "Nuova nota" al centro dell'area board) |
| Top bar | **Svuota** (con dialog di conferma) · **Elimina** (attivo solo con selezione) · **Centra** (attivo a layout misurato) · **Azzera** · **Importa** (SAF, `*/*`) · **Esporta** (SAF, scrive `board.zib`) |
| Selezione | Tap su elemento = selezione; tap su spazio vuoto = deselezione; con SELEZIONA il gesto diventa MOVE, HANDLE o ROTATE secondo il hit |
| Ridimensiona | **Quattro maniglie**, una per angolo (v1.8), presa 28 dp: trascinando un angolo resta fermo quello opposto. Sulle note scala il corpo del testo (larghezza fissa 220 dp) |
| Rotazione foto | **Doppio tocco su una foto** → le maniglie diventano quattro frecce curve; trascinandole la foto ruota attorno al centro, con aggancio a 0/90/180/270° entro 4° |
| Tratti | La gomma ne erode la porzione toccata (residui ≥ 2 punti restano); la selezione mostra il bbox; la maniglia su un tratto non ridimensiona ancora (TODO) |
| Editor nota | Doppio tap rapido (< 400 ms, `DOUBLE_TAP_MS`) sulla nota → `TextField` inline, IME "FINE"; il resto della UI non "ruba" l'input |
| Portabilità | `.zib` = ZIP: `manifest.json` (version/name/panX/panY/zoom/elements) + `media/*.jpg` (URI relativi `media/<basename>`) |

## 3. Stack e versioni

| Ruolo | Stack |
|---|---|
| Lingua | Kotlin **1.9.24** |
| UI | Jetpack Compose **1.6.0** (BOM 2024.01.00) + Compose Compiler **1.5.14** + Material3 (BOM) + `com.google.android.material:1.11.0` |
| Build | AGP **8.3.2**, Gradle **8.6** (wrapper), JDK **17** (Temurin 17.0.20), Android platform **34** |
| Dati | `kotlinx-serialization-json` 1.6.3, coroutines 1.8.0, lifecycle 2.7.0, activity 1.8.2 |
| Immagini | Coil **2.5.0** (`AsyncImage`) |
| Target | minSdk **26**, targetSdk **34** |
| Versione app | **1.9** (versionCode **10**), `applicationId` `it.zibaldone.app` |
| Lint | ktlint **1.2.1** via plugin `org.jlleitschuh.gradle.ktlint` 12.1.0 (dalla v1.7) |

## 4. Architettura delle fonti (attuale)

```
zibaldone/
├── app/
│   ├── build.gradle.kts                  # minSdk 26, target 34, version 1.9/10
│   └── src/main/
│       ├── AndroidManifest.xml               # label = @string/app_name
│       ├── java/it/zibaldone/app/
│       │   ├── MainActivity.kt               # entry, provider VM
│       │   ├── model/
│       │   │   ├── BoardElement.kt           # sealed serializzabile (Drawing/TextNode/ImageNode)
│       │   │   └── BoardId.kt                # contatore atomico degli id (v1.7)
│       │   ├── ui/
│       │   │   ├── BoardScreen.kt            # scaffold, layering, top/bottom bar, FAB, dialog
│       │   │   ├── BoardGesture.kt           # dispatcher unico di tutte le gesture
│       │   │   ├── HitTesting.kt             # hit-testing manuale in spazio-schermo + HandleCorner
│       │   │   ├── StrokeCanvas.kt           # StrokesCanvas / StrokeNode / EraserPreview / SelectionBox / Handles
│       │   │   ├── TextNodeOverlay.kt        # nota: render + editor on-demand
│       │   │   └── ImageNodeOverlay.kt       # foto: render ruotato + overlay di selezione
│       │   ├── util/
│       │   │   ├── CanvasMath.kt             # world<->screen + trimStroke (resample 6px) + distanze
│       │   │   └── ExportImportManager.kt    # .zib (zip+manifest), su Dispatchers.IO
│       │   └── view/
│       │       ├── CameraState.kt            # panX/panY/zoom FloatState + pinch ancorato
│       │       └── BoardViewModel.kt         # VM: attrezzi, selezione, editing, rotazione, tratti, board
│       └── res/values/strings.xml            # app_name = "Zibaldone"
├── build.gradle.kts / settings.gradle.kts / gradle.properties / local.properties
├── .editorconfig                             # regole ktlint del progetto
└── gradle/wrapper/
```

### 4.1 Layering dello schermo (ordine di disegno)

`Box` bianco (fillMaxSize) contiene, in questo ordine:
1. **layer gesture**: `Box(Modifier.fillMaxSize().pointerInput { boardDispatch(vm, densityPx) })`
2. **`StrokesCanvas`**: tutti i tratti commitati **+** il tratto in corso —
   oggi ogni tratto è un **nodo autonomo** §4.3
3. **layer nodi**: `for (element in elements)` → `TextNodeOverlay` /
   `ImageNodeOverlay`
4. **`StrokeSelectionBox`**: contorno + maniglia del tratto selezionato
5. **`EraserPreview`**: anello del raggio gomma in spazio-schermo
6. fuori dal box: top bar, bottom bar, FAB, `AlertDialog` di conferma

### 4.2 Principio architetturale

Un **single gesture dispatcher** (`PointerInputScope.boardDispatch`)
decide tutto l'input (hit testing in `HitTesting`, modalità MOVE/HANDLE/
PEN/ERASE/CAMERA); i nodi non hanno gesture proprie (il `TextField`
dell'editor, montato solo in editing, usa `pointerInput` per le
selection handles). Selezione, spostamento, resize, erase, pan, zoom
passano tutti dagli stessi `vm.*` e si compongono senza conflitti.

### 4.3 Rendering dei tratti — "tratto = nodo" (v1.5, stabile in v1.6)

**Ogni tratto è un composable `StrokeNode` costruito come le note e le
foto** (che su tablet funzionano da sempre con qualsiasi camera):

1. In **body-scope** (dove lo snapshot-tracking Compose funziona su
   questo dispositivo): leggo `camera.zoom` e applico
   `camera.worldToScreen` ai due angoli del bbox mondo del tratto
   (ampiato di `strokeWidth/2` per i cap round).
2. **Layout**: `Modifier.offset { IntOffset(tl) } + .size(widthPx/density dp,
   heightPx/density dp)` → la scatola è in pixel-schermo e si muove con
   la camera come qualsiasi altro nodo.
3. **Contenuto**: i punti del tratto sono già trasformati *in
   body-scope* in coordinate locali del canvas:
   `(p − bboxMin) * zoom`, e la larghezza è `strokeWidth * zoom`. La
   closure di disegno (`drawLine` per 2 punti / `drawPath` con
   `Stroke(Round, Round)` altrimenti) **itera solo dati precalcolati**:
   zero letture di camera dentro la closure, zero `DrawScope`
   translate/scale, zero `graphicsLayer`.

**Perché così** (cruciale — vedi §14 "Riscontro device" per la storia):
sulla tablet dell'utente, gli aggiornamenti della camera **non
propagano** nelle trasformazioni applicate a tempo di disegno
(`DrawScope.translate/scale` dentro la closure, v1.2) né nelle
`graphicsLayer` (block in v1.1, property-style in v1.3): i tratti
restavano "fermi" o "a distanza". Lo stesso cambio di stato, applicato
**via layout (offset/size) da letture in body-scope**, si propaga
sempre ed è esattamente il meccanismo della `TextNodeOverlay`.

Conseguenze pratiche: nessun buffer gigante (ogni canvas è piccolo e
locale), nessun clipping possibile, risoluzione nativa a ogni zoom,
nessun costo GPU di layer, e **l'identità di input**
`screen = pan + zoom·(screen−pan)/zoom` è garantita dallo stesso
singolo passaggio di trasformazione.

### 4.4 Perf / perché non "scatta" più

- `panX/panY/zoom` sono **`mutableFloatStateOf`** — osservazioni
  granulari di 3 scalari.
- Ogni ricomposition da camera tocca solo: layout di
  `StrokeNode`/nodi (offset/size) + il `EraserPreview`.
- Il tratto in corso: `activeStroke` + `liveStrokeVersion` →
  `StrokesCanvas` rilegge solo per `updateStroke`.
- La gomma: solo gli elementi toccati entrano in `eraseAt` (che fa
  `clear + addAll` sulla lista).

## 5. Modello dati (struttura del pacchetto invariata)

```kotlin
@Serializable sealed class BoardElement {
    abstract val id: Long
    abstract val kind: String           // discriminate JSON

    data class Drawing(  id, points: List<SerializablePoint>,
                         color: Int = 0xFF000000.toInt(), strokeWidth: Float = 4f )
    data class TextNode( id, text, position: SerializablePoint,
                         fontSize: Float = 20f, color: Int = 0xFF000000.toInt() )
    data class ImageNode(id, imageUri, position: SerializablePoint,
                         scale: Float = 1f, rotation: Float = 0f )
}
```

`.zib` = ZIP con `manifest.json` (`BoardManifest(version=1, name,
panX, panY, zoom, elements)`) + `media/*.jpg` con URI relativi
`media/<basename>.jpg`. **Il formato non è mai cambiato**: board
esportati in passato restano importabili.

## 6. Camera

`view/CameraState.kt` — `panX`, `panY`, `zoom`
(`mutableFloatStateOf`, zoom clamp 0.1–10):

```
world  = (screen − pan) / zoom
screen = world * zoom + pan
panBy(Δscreen)
pinch(prevCentroid, centroid, prevSize, size)
```

**Pinch ancorato al baricentro** (le cose non volano via):
```
k     = newZoom / zoom
pan'  = c − (c − pan) * k   +   (c − c_prev)          [c = baricentro corrente]
```
cioè: zoom ancorato sul punto sotto le dita + traslazione del
baricentro (il movimento "pan" durante la stretta).

## 7. Gesture (dispatcher unico)

`ui/BoardGesture.kt` — `suspend fun PointerInputScope.boardDispatch(vm, densityPx)`
(`awaitEachGesture` + loop `awaitPointerEvent()`).

| Dito | SELEZIONA | PENNA | GOMMA |
|---|---|---|---|
| 1 su elemento | tap = seleziona; ≥ slop = **sposta** (Δ/zoom, mondo); doppio tap rapido = **editor nota** | — | — |
| 1 su maniglia | tap = no-op; ≥ slop = **ridimensiona** (nota: font; foto: scala) — il tratto ha la maniglia ma non ridimensiona ancora | — | — |
| 1 su spazio vuoto | tap = deseleziona; drag = **pan** | **traccia** | **cancella parziale** |
| ≥ 2 | **pinch-zoom ancorato** + pan | idem (tratto in corso scartato) | idem |

Dettagli e regole apprese sulle iterazioni:

- `MOVE_SLOP_PX = 12f`: sotto lo slop = tap (solo selezione/manipolazione);
  sopra = MOVE/HANDLE. Elimina i micro-trascinamenti che in v1.1 facevano
  "sparire" gli elementi con un tocco leggero.
- **`lastPointer` va aggiornato in TUTTI i rami del loop** — in v1.4 ho
  trovato il bug del "pan che salta al rilascio di un dito durante il
  pinch": il ramo a ≥ 2 dita terminava con `continue` senza aggiornare
  `lastPointer`; al primo evento a 1 dito il delta era la distanza
  accumulata durante tutto il pinch. Fix: `lastPointer = centroid` a ogni
  evento a ≥ 2 dita (fallback `pressed[0]`).
- Doppio tap: in MOVE con `< slop` e `< 330 ms` dal tap precedente su
  stessa nota → `startEditingText`.
- Pan = `camera.panBy(changed − last)`.

## 8. Overlays (composabili dei nodi)

### 8.1 `TextNodeOverlay`
- Render non interattivo: `Text` + (se selezionata) bordo blu;
- `isEditing` (doppio tap): `Text` sostituito da `TextField` bianco,
  `FocusRequester` al mount, IME action **FINE** (chiude editing
  salvando), croce per annullare/chiudere;
- **layout = `offset { worldToScreen(pos) } + width(220dp·zoom)`**, testo a
  `fontSize·zoom`, altezza `max(28dp, 18dp·righe·zoom)` — tutto letto in
  body-scope (il pattern §4.3 che funziona in ogni versione).

### 8.2 `ImageNodeOverlay`
- `AsyncImage` (Coil) `ContentScale.Crop` dentro `size(200dp·scale·zoom)`;
  bordo blu se selezionata; maniglia comune.

### 8.3 `StrokeCanvas.kt`
- `StrokesCanvas`: body-scope legge `elements` + `activeStroke` +
  `liveStrokeVersion` e monta **un `StrokeNode` per ogni** (commitati +
  in corso), §4.3;
- `EraserPreview`: anello in spazio-schermo al raggio esatto del dito;
- `StrokeSelectionBox` + `SelectionHandle`: cornice del bbox del tratto
  selezionato + maniglia (14 dp blu, centro bianco 4 dp).

## 9. Build (ambiente + protocollo di consegna)

### 9.1 Ambiente (tutto sotto `~/`, nessun sudo)
| Componente | Path | Versione |
|---|---|---|
| JDK | `~/tools/jdk-17.0.20.1+1` (anche `~/java/jdk-17.0.20.1+1`) | Temurin 17.0.20 |
| Android SDK | `~/android-sdk` | platform-34, build-tools 34.0.0 (licenze accettate) |
| Gradle | wrapper **8.6** nel progetto | 8.6 |

### 9.2 Comandi (build di consegna)
```bash
cd ~/zibaldone
export JAVA_HOME=$HOME/tools/jdk-17.0.20.1+1   # o ~/java/jdk-17.0.20.1+1
export ANDROID_HOME=$HOME/android-sdk
./gradlew :app:clean :app:assembleDebug --console=plain --offline
```

### 9.3 Protocollo di consegna (applicato a ogni versione)
1. `app/build.gradle.kts`: **`versionCode` +1 e `versionName` → nuova etichetta**
   (obbligatorio: il file con lo stesso nome su tablet non aggancia i
   nuovi dex — in v1.0 l'app "sembrava identica" così).
2. Build (9.2).
3. `cp app-debug.apk ~/Zibaldone-v<nome>.apk` e **rimuovi l'APK
   precedente**: in `~/` deve esistere **un solo** installabile.
4. **Verifica dex**: `unzip` tutti i `classes*.dex`, `strings | grep -c
   <simbolo-nuovo>` — su ogni dex (D8 sparge nei N bucket).
5. Append una sezione a questo file con sintomi/root-cause/fix.
6. Messaggio di consegna in italiano con la procedura di test.

## 10. Lezioni di compilazione (in ordine, tutte le sessioni)

1. Tema `Theme.Material3.DayNight.NoActionBar` mancante →
   `com.google.android.material:material:1.11.0`
2. Pairing Compose Compiler 1.5.14 ↔ Kotlin 1.9.24
3. AGP 8.3.2 richiede Gradle ≥ 8.4 → wrapper **8.6**
4. `Color.value` in Compose 1.6 è `ULong` → colori come **ARGB Int**
5. `StrokeCap`/`StrokeJoin` sono in `androidx.compose.ui.graphics`
6. `graphicsLayer` (named) in `androidx.compose.ui.graphics`
7. `awaitEachGesture`/`awaitFirstDown` in `foundation.gestures`;
   `awaitPointerEvent()` è membro dello scope gesture
8. `PointerInputChange` non espone `.delta` → pinch via
   `calculateCentroid()`+`calculateCentroidSize()`
9. `getDistance` rimosso → helper `Offset.distance()` locale (`ui/HitTesting.kt`)
10. `viewModel()` → `lifecycle-viewmodel-compose:2.7.0`

**Sessione v1.1 (revamping interattivo):**
11. Virgola di argument a piè di un blocco lambda: le virgole di troncamento
    sono legali dentro `f(...)`, **non** dentro `{ ... }`
12. `awaitEachGesture` ha receiver **`PointerInputScope`** → il dispatcher
    è `suspend fun PointerInputScope.boardDispatch(...)`
13. `Offset.distanceTo()` **non esiste** in `ui.geometry`
14. `ImeAction` vive in **`androidx.compose.ui.text.input`** (non `ui.input.ime`)
15. `Modifier.focusRequester` è un'estensione separate da importare
    esplicitamente (`androidx.compose.ui.focus`)
16. In Compose 1.6 non esiste `Modifier.offset(IntOffset)` né
    `offset(Int, Int)`: usare `offset { IntOffset(x, y) }`
17. `val ... by mutableStateOf` non si riaggancia: `var` per
    `activeStroke`/`liveStrokeVersion`
18. `fun setEraserPreview(Offset?)` collidiva col setter della property
    `var eraserPreview: Offset?` (stessa firma JVM) → via la funzione

**Sessioni v1.2–v1.6:**
19. `MutableStateList.replaceAll(list)` non è accettata con `List<T>` in
    questo ambiente (firma `UnaryOperator`); uso `clear() + addAll()`
20. L'import `androidx.compose.graphics.TransformOrigin` non si risolveva
    (build spezzato a runtime di tooling); l'import funzionante:
    `androidx.compose.ui.graphics.TransformOrigin` (dopo v1.4 non serve più)
21. `Modifier.size(width, height)` vuole **`Dp`**, non `Float`:
    `(px / density).dp`
22. `drawCircle`/`drawLine`/`drawPath` sono **membro di `DrawScope`**
    (niente import); `Stroke` e `Path` servono gli import
    `drawscope.Stroke` / `graphics.Path`

## 11. Cronologia versioni e root cause

| v | build | Cambi |
|---|---|---|
| v1.0 | 1 | Primo build funzionante, penna funzionava solo a pan=0/zoom=1 |
| v1.1 | 2 | Revamping interattivo completo (toolbar, attrezzi, note/foto, editor, pinch ancorato, gomma, `Centra`/`Azzera`, `.mboard`), `transformOrigin` corretto |
| v1.2 | 3 | Pan stabile (slop), `StrokesCanvas` unificato screen-space, gomma **parziale** (`trimStroke`), `Centra` (fit-to-content) |
| v1.3 | 4 | Tentativo `graphicsLayer` property-style + buffer canvas mondo — **fallito su tablet** (tratti "fermi", pan instabile) |
| v1.4 | 5 | **`StrokeNode`** (tratto = nodo, layout) + fix `lastPointer` nel pinch |
| v1.5 | 6 | **Fix del bug geometrico**: i punti del canvas vengono trasformati `(p−min)·zoom` in body-scope (il Box era screen-space, il contenuto era in mondo → slittava a zoom ≠ 1) |
| v1.6 | 7 | **Pulsante `Elimina`** (toglie solo l'elemento selezionato) nel top bar |
| v1.7 | 8 | **Audit del codice**: 14 bug corretti (ANR su export/import, immagini importate in cache, bbox `Float.MIN_VALUE`, quota nota senza `densityPx`, `Centra` su schermo intero, hit test sui vertici, deriva del pinch, id da timestamp) + ktlint |
| v1.8 | 9 | **Maniglie ai 4 angoli** (ancoraggio all'angolo opposto, area di presa 28dp) + **rotazione delle foto** con frecce curve al doppio tocco (`rotation` finalmente esposto in UI) |
| v1.9 | 10 | **Rinomina in Zibaldone**: `applicationId`/package `com.moodboard.app` → `it.zibaldone.app`, label, titolo in-app, classi `Moodboard*` → `Board*`, estensione `.mboard` → `.zib` |

**v1.2 — sintomi su v1.1 e fix** (dettaglio):
1. Tocco leggero = micro-trascinamento applicato subito → **slop 12 px**.
2. Contenuto "scomparso" dopo pan lontano: il canvas dei tratti era un
   buffer grande come lo schermo in coordinate-mondo dentro il layer, e
   tagliava definitivamente i tratti fuori finestra; in più
   `transformOrigin` di default (centro) desincronizzava zoom e nodi. →
   un solo `StrokesCanvas` full-screen che applica la trasformazione
   dentro la draw-call (nessun clipping) + camera `pan(px-screen)+zoom`.
3. La gomma cancellava l'intero tratto → `trimStroke`:
   resample a passo 6 px, mantenendo i punti fuori dal raggio e i residui
   con ≥ 2 punti (nuovi `Drawing`, id `nanoTime`). Raggio 12–64 dp
   (default 28), anello di anteprima a raggio esatto.
4. Aggiunta `Centra` (bbox elementi + padding 40 px, clamp 0.1–10,
   centrata su schermo) e `Azzera` (`pan=(0,0), zoom=1`).

**v1.3 — tentativo di "la penna torna a seguire il dito"**
Sintomo su v1.2: dopo zoom/pan il tratto non seguiva più il dito (tutto
il resto OK). Ipotesi confermata: la trasformazione camera applicata
**nella** closure di disegno non si aggiornava con lo stato camera su
quella tablet, mentre il mechanism LAYOUT usato dai nodi sì. Fix
(v1.3): `graphicsLayer` property-style con `transformOrigin=(0,0)` sulla
camera Box (letture body-scope) + contenuto canvas "puro mondo" (buffer
calcolato attorno al contenuto, soffitto 8192 px).

**v1.3 non funzionava: sintomi su tablet**
I tratti restavano **fermi sul posto** (il layer non si aggiornava); il
pan/zoom dava di ipersensibilità: **al rilascio di un dito tutto
saldava** (il bug di `lastPointer` §7).

**v1.4 — architettura "tratto = nodo"**
- Fix `lastPointer = centroid` in ogni evento a ≥ 2 dita (§7).
- Ogni tratto → `StrokeNode`: `worldToScreen` su bbox in body-scope +
  `offset/size` (layout) + `Canvas(Modifier.fillMaxSize())` locale.
  Stesso meccanismo delle note/foto — l'unico provato su device.

**v1.4 aveva ancora un bug (risolto in v1.5)**
A zoom ≠ 1 il contenuto non era moltiplicato per `zoom` (Box screen-space,
punti in mondo): il tratto "scivolava" via dalla posizione tracciata
proprio al momento dello zoom — esattamente quanto osservato.

**v1.5 — trasformazione completa (stabile)**
`(p − bboxMin)·zoom` come lista `localPts` in body-scope; larghezza
`strokeWidth·zoom`; la closure itera solo `localPts` + `strokeW` — nessun
stato dentro. Stessa forma della `TextNodeOverlay` (body → transform →
layout), che per l'utente funziona in ogni versione.

**v1.6 — `Elimina`**
Top bar: `TextButton(enabled = selectedId != null) {
viewModel.removeElement(id) }`. Il VM già aveva `removeElement`
(rimuove, azzerà la selezione, esce dall'editing se la nota era in
editing). Funziona per tratti/note/foto.

**v1.7 — audit completo del codice (14 bug)**
Nessun sintomo segnalato dall'utente: è una rilettura integrale dei 12
file sorgente alla ricerca di difetti latenti. Ordinati per gravità.

*Critici — perdita di dati / blocco dell'app*
1. **ANR su esporta/importa/aggiungi foto.** `ExportImportManager.saveBoard`
   e `loadBoard` erano `suspend` ma senza `withContext(Dispatchers.IO)`, e
   `viewModelScope` è Main: tutto lo zip (e la copia della foto scelta in
   `addImageFromUri`) girava sul thread UI. → `Dispatchers.IO` nei tre punti.
2. **Immagini delle board importate in `cacheDir`.** `loadBoard` estraeva in
   `cacheDir/imports/<ts>/` e i nodi puntavano lì per sempre: Android svuota
   la cache quando serve spazio → foto rotte. Peggio, riesportando,
   `saveBoard` saltava in silenzio i file mancanti producendo un `.mboard`
   il cui manifest citava immagini assenti. → estrazione in
   `filesDir/imports/<ts>/` (persistente), pulizia delle import precedenti
   dopo un load riuscito, e `SavedBoard.missingMedia` riportato nel Toast.

*Alti — errori visibili*
3. **`Float.MIN_VALUE` come seme del massimo** (è il più piccolo float
   *positivo*, 1.4E-45, non il più negativo): un tratto interamente a
   coordinate-mondo negative lasciava `maxX/maxY` a ~0. In
   `StrokeSelectionBox` il riquadro blu si allungava fino all'origine del
   mondo; in `HitTesting.strokeBbox` gonfiava il bbox. → `NEGATIVE_INFINITY`
   (come già faceva `StrokeNode`).
4. **`nodeRect` delle note: `densityPx` mancante nell'altezza.** La larghezza
   lo aveva, il termine delle righe no → su schermo 2-3x il rettangolo
   finiva molto sopra la nota disegnata: metà bassa non selezionabile e
   **maniglia di resize non afferrabile dove è dipinta** (il disegno usa
   l'angolo reale del Box, l'hit test usava la stima). → altezza allineata
   al rendering: `(10dp + righe · fontSize · 1.4) · zoom · densityPx`,
   `ceil` invece di `roundToInt` sulle righe.
5. **`Centra` con le foto.** `fitToContent` trattava `position` come centro
   (`± half`), mentre rendering e hit test la usano come **angolo
   alto-sinistra**. → bbox coerente (`position` → `position + lato`).
6. **`Centra` e inserimento "al centro" misuravano lo schermo intero**
   (`LocalConfiguration`), non l'area board, che è quel che resta tolte top
   bar e bottom bar (150+ dp): zoom sovrastimato, centratura verticale
   sbagliata e note/foto nuove sotto il centro visibile. → `onSizeChanged`
   sul Box della board e `canvasCenterPx()`.

*Medi*
7. **Hit test e gomma solo sui vertici registrati**: con il dito veloce i
   punti distano decine di unità e il centro del segmento non si
   selezionava né si cancellava. → `CanvasMath.distanceToSegmentSq` +
   `polylineContains`, usati da `strokeContainsPoint` e da `eraseAt`.
8. **Deriva del pinch.** La formula era
   `c − (c − pan)·k + (c − c0)`: sommava lo spostamento del centroide sopra
   un'ancora che già traslava, con errore `(c − c0)·(1 − k)` per frame
   (esatta solo a `k = 1`, cioè pan puro). → legge corretta
   `newPan = c − (c0 − pan)·k`.
9. **Gomma: `clear()` + `addAll()` a ogni evento di movimento** invalidava
   tutti i nodi della board a ogni frame. → splice in-place scorrendo
   all'indietro; il primo pezzo superstite **mantiene l'id originale**
   (selezione preservata, meno churn).
10. **`trimStroke` duplicava i punti.** Il resample partiva da `k = 0`,
    riemettendo il vertice condiviso tra due segmenti: i punti
    raddoppiavano a ogni passata di gomma e finivano nel `.mboard`. → da
    `k = 1`. Tolto anche lo scarto dei segmenti con primo punto == ultimo.

*Bassi*
11. **Zip-slip senza separatore**: `startsWith(packageDir.canonicalPath)`
    accettava cartelle sorelle con prefisso comune (`.../imports/123` vs
    `.../imports/1234evil`). → `+ File.separator`.
12. **Id da timestamp** (`currentTimeMillis()` nei default, `nanoTime()`
    nella gomma): due elementi nello stesso millisecondo condividevano l'id
    e selezione/spostamento/eliminazione agivano su entrambi. → `BoardId`
    (contatore atomico seminato dall'orologio) + `BoardId.observe()` sugli
    id importati, così un `.mboard` da un altro device non può collidere.
13. **`offset { IntOffset(x.toInt(), …) }`**: il troncamento tende a zero, il
    nodo che attraversa l'origine sfarfallava di 1px in modo asimmetrico. →
    `roundToInt()` nei quattro overlay.
14. **`BoardManifest.version` mai controllato** in lettura. → confronto con
    `CURRENT_FORMAT_VERSION` ed errore esplicito su formato più recente.

*Contorno*
- Aggiunto **ktlint** (`org.jlleitschuh.gradle.ktlint` 12.1.0, ktlint 1.2.1)
  con `.editorconfig`: trailing comma disabilitate (stile del progetto) e
  `@Composable` esentati dalla regola sul nome minuscolo.
  `./gradlew :app:ktlintCheck` / `:app:ktlintFormat`.
- Corretto il commento di testata di `MoodboardScreen`, che descriveva
  ancora la camera come `graphicsLayer` (architettura v1.1-v1.3, smentita
  su questa tablet) mentre il codice è "tratto = nodo" dalla v1.4.
- Il formato `.mboard` **non è cambiato**: le board v1.6 si aprono in v1.7.

**v1.8 — maniglie ai quattro angoli e rotazione delle foto**
Richiesta dell'utente: maniglie più comode da afferrare e, al secondo
tocco, frecce curve per ruotare le foto.

*Maniglie di ridimensionamento (`SelectionHandles`)*
- Da **1 a 4**, una per angolo (`HandleCorner`), disegnate **centrate
  sull'angolo** (offset di mezza maniglia verso l'esterno) invece che
  7dp all'interno: prima il pallino non stava sopra la propria area di
  presa. Visivamente 20dp, bianche con bordo blu da 3dp; area di presa
  `HANDLE_RADIUS_DP` da 22 → **28dp**. `nearestCorner()` sceglie l'angolo
  più vicino, così su un nodo piccolo le aree sovrapposte si risolvono
  senza ambiguità.
- **Ancoraggio all'angolo opposto**: trascinando un angolo, quello
  diagonalmente opposto resta fermo in coordinate-mondo
  (`resizeImageAnchored` ricalcola `position` dal nuovo lato).
- **Corretto un bug del resize** presente fino alla v1.7: l'ancora era
  l'angolo *afferrato*, quindi `handleStartDist = down.distance(corner)`
  valeva ~0 (clampato a 1px) se toccavi la maniglia al centro — e il
  primo pixel di movimento mandava la scala al suo limite. Ora l'ancora è
  la diagonale, distanza iniziale sempre significativa.
- Le note scalano solo il **corpo del testo** (larghezza fissa a 220dp) e
  restano ancorate al proprio top-left; `scaleText(factor)` incrementale
  è diventato `setTextSize(newSize)` **assoluto**, calcolato dalla
  dimensione al momento della presa: nessuna deriva accumulata durante la
  gesture.

*Rotazione delle foto (`RotationHandles`)*
- **Doppio tocco su una foto** → `rotatingId`: le quattro maniglie di
  resize sono **sostituite** da quattro frecce curve
  (`Icons.Filled.RotateRight`, 30dp su disco blu), così un angolo non
  significa mai due cose insieme.
- Doppio tocco su una **nota**: invariato, apre l'editor di testo.
- Trascinando una freccia si ruota attorno al centro del nodo
  (`atan2` del vettore centro→dito, delta sommato alla rotazione
  iniziale). **Aggancio a 0/90/180/270°** entro 4°, per raddrizzare
  facilmente.
- Si esce dalla modalità toccando qualsiasi altra cosa (`select()` azzera
  `rotatingId`).
- Il campo `rotation` di `ImageNode` **esisteva già** dalla v1.1 ed era
  serializzato ma mai esposto (era in §13 tra i limiti noti): il formato
  `.mboard` quindi **non cambia**, le board v1.7 si aprono in v1.8 e
  viceversa.

*Rendering — attenzione alla regola del device (§14)*
La foto è ruotata con `Modifier.rotate()` (che internamente è
`graphicsLayer`), ma **la camera non passa di lì**: `rotate` agisce solo
sul disegno, dopo `offset`/`size`, quindi pan/zoom continuano a entrare
nel nodo esclusivamente per layout. La rotazione è un valore statico
dell'elemento, non stato camera letto in una closure — il caso che
falliva in v1.1-v1.3.
Il frame di selezione e le maniglie sono un **Box fratello NON ruotato**
posizionato con lo stesso `offset`/`size`: restano allineati agli assi e
quindi coerenti con l'hit test, che rimane un semplice rettangolo.
*Limite noto*: a rotazioni non multiple di 90° la foto sporge dal
riquadro di selezione e gli angoli dell'area di tocco non seguono
l'immagine ruotata (il centro funziona sempre).

**v1.9 — l'app diventa Zibaldone**
Nessun cambiamento funzionale: solo identità. L'utente non aveva mai
usato l'app per davvero (solo test), quindi si è potuto cambiare anche
l'`applicationId`, che altrimenti avrebbe comportato la perdita dello
storage locale.

| Cosa | Prima | Dopo |
|---|---|---|
| `applicationId` / `namespace` | `com.moodboard.app` | `it.zibaldone.app` |
| Package Kotlin | `com.moodboard.app.*` | `it.zibaldone.app.*` |
| Label (launcher) | `"Moodboard App"` scritta a mano nel manifest | `@string/app_name` → **Zibaldone** |
| Titolo in-app | `Text("Moodboard")` letterale | `stringResource(R.string.app_name)` |
| `rootProject.name` | `MoodboardApp` | `Zibaldone` |
| Classi | `MoodboardViewModel`, `MoodboardScreen` | `BoardViewModel`, `BoardScreen` |
| Estensione export | `board.mboard` | `board.zib` |
| APK di consegna | `~/Moodboard-v<n>.apk` | `~/Zibaldone-v<n>.apk` |

Note:
- La label e il titolo ora passano **entrambi** da `strings.xml`. Prima
  il manifest aveva la stringa letterale e `app_name` non era usato da
  nessuno: cambiare nome richiedeva di ricordarsi di due posti.
- I nomi delle classi sono passati a `Board*` invece che `Zibaldone*`:
  il dominio del codice è già tutto `BoardElement`/`BoardGesture`/
  `BoardId`/`BoardHit`/`BoardTool`/`BoardManifest`, quindi il prefisso
  coerente è `Board`. Così il nome commerciale dell'app resta confinato
  a `strings.xml` e al package.
- **La struttura del pacchetto esportato non cambia** (ZIP con
  `manifest.json` + `media/`): è cambiata solo l'estensione suggerita in
  esportazione. I vecchi `.mboard` **si importano ancora**, perché il
  picker di importazione accetta `*/*` e legge lo ZIP per contenuto.
- `applicationId` diverso significa che per Android è **un'altra app**:
  la vecchia "Moodboard App" resta installata a fianco e va disinstallata
  a mano; i suoi dati (`filesDir`) non migrano.
- Verificato con `aapt2 dump badging`: `package: name='it.zibaldone.app'`,
  `application-label:'Zibaldone'`. Nei dex: **0** occorrenze di
  `moodboard`.

## 12. Verifica

- Build: `:app:clean :app:assembleDebug` → **BUILD SUCCESSFUL** (v1.9).
- Lint: `:app:ktlintCheck` → **BUILD SUCCESSFUL** (v1.9).
- Dex (verificati su ogni `classes*.dex`):
  | v | marker grep |
  |---|---|
  | v1.2 | `trimStroke`, `fitToContent`, `eraseAt`, `StrokesCanvas`, `MOVE_SLOP_PX` |
  | v1.5 | `localPts` |
  | v1.6 | `"Elimina"` |
  | v1.7 | `BoardId` (classes4: 3, classes5: 1), `polylineContains` (classes5: 1, classes6: 2), `distanceToSegmentSq` (classes6: 2), `SavedBoard` (classes5: 1, classes6: 3), `canvasCenterPx` (classes5: 2) |
  | v1.9 | `zibaldone` in 6 dex (classes2: 3, classes3: 24, classes4: 45, classes5: 16, classes6: 183, classes7: 30), `BoardViewModel` (classes4: 41), `BoardScreen` (classes6: 174); **0** occorrenze di `moodboard` |
  | v1.8 | tutti in classes5: `HandleCorner` (8), `RotationHandles` (7), `RotateHandle` (4), `resizeImageAnchored` (3), `setImageRotation` (2), `startRotating` (1) |
- **Checklist tablet (v1.8):**
  1. Seleziona una foto → **quattro** maniglie bianche/blu, una per angolo,
     centrate sull'angolo.
  2. Trascina ciascuno dei quattro angoli → la foto scala e **l'angolo
     opposto resta fermo**; nessun salto al primo pixel di movimento.
  3. **Doppio tocco su una foto** → le maniglie diventano quattro frecce
     curve blu.
  4. Trascina una freccia → la foto ruota attorno al centro; vicino a
     0/90/180/270° si aggancia.
  5. Tocca altrove → si esce dalla rotazione, tornano le maniglie.
  6. **Doppio tocco su una nota** → editor di testo, come prima (non ruota).
  7. Esporta con una foto ruotata, importa → la rotazione è conservata.
- **Checklist tablet (v1.7):**
  1. Nota grande: selezionala e **afferra la maniglia blu esattamente dove
     la vedi** (prima bisognava toccare più in alto). Vale anche per il
     tocco sulla metà bassa della nota.
  2. Sposta la camera in alto a sinistra (mondo negativo), disegna un
     tratto e selezionalo → il riquadro blu **avvolge il tratto**, non si
     allunga verso il centro della board.
  3. Con qualche foto sulla board premi **Centra** → tutto entra nello
     schermo, centrato nell'area board (non sotto la bottom bar).
  4. Aggiungi una nota col FAB → nasce **al centro visibile**.
  5. Disegna una linea lunga muovendo il dito **veloce**, poi tocca a metà
     tra due punti → si seleziona; con la gomma, attraversala veloce → si
     cancella.
  6. Pinch zoom **spostando** contemporaneamente le dita → nessuna deriva.
  7. **Esporta** una board con foto → nessun ritardo/blocco della UI;
     **Importa** su una board pesante → idem. Se un'immagine mancasse, il
     Toast lo dice ("N immagini non sono state trovate").
  8. Importa una board, chiudi l'app, riaprila, riapri quella board → le
     foto ci sono ancora (prima potevano sparire con la pulizia cache).
- **Checklist tablet (base, da v1.6):**
  1. Disegna alcuni tratti con la penna → seguono il dito.
  2. Pinch-in / pinch-out → tutto cresce/rimpicciolisce **sotto le dita**;
     al rilascio di un dito niente salto.
  3. Pan a 1 dito su spazio vuoto → il mondo segue il dito; nota/foto/
     tratti restano allineati tra loro.
  4. Gomma: attraversa il centro di una linea → solo la porzione
     attraversata scompare.
  5. Nota: tap = seleziona; doppio tap = editor; FINE = chiuso;
     maniglia = ridimensiona; **Elimina** = via.
  6. Foto: import, tap, spostamento, maniglia, **Elimina**.
  7. Perduto? **Centra**. Torna a origine? **Azzera**.
  8. **Esporta** → `.mboard`; riavvia; **Importa** → board identico,
     stessa inquadratura (pan/zoom salvati nel manifest).

## 13. Limiti noti / roadmap

- Nessun **undo/redo**: la gomma è distruttiva (parziale però),
  `Elimina` e `Svuota` idem.
- Nessun **selettore colore** (tratti e testo fissi a nero; il modello ha
  già il campo `color` — basta un slider).
- **Un solo board** per sessione; Room è dichiarata ma non usata.
- ~~`rotation` delle foto è persistita ma non esposta in UI~~ →
  **fatto in v1.8** (doppio tocco → frecce curve). Resta che a rotazioni
  non multiple di 90° la foto sporge dal riquadro di selezione e l'area
  di tocco non segue l'immagine ruotata (resta assiale): per un hit test
  fedele servirebbe la trasformazione inversa del punto.
- La **larghezza della nota** è stimata (220 dp × zoom, altezza
  `max(28, 18·righe) dp × zoom`) — parole lunghissime possono uscire
  dall'hitbox.
- Maniglia sul **tratto selezionato**: è presente ma non ridimensiona
  ancora (manca il scale dei punti + strokeWidth in `vm`).
- Prestazioni su board **molto** densi (centinaia di tratti): ogni tratto
  è un composable — se un giorno diventa collo di bottiglia, ottimizzare
  con batch o `drawBehind` in un canvas "già trasformato" solo per lo
  statico.

## 14. Riscontro device (tablet dell'utente, Compose 1.6.0) — lezione

Questa è la sezione da preservare. **Due meccanismi di
"trasformazione camera" si comportano in modo diverso su questo
dispositivo:**

1. **AFFIDABILE** (usato in v1.4/1.5/1.6):
   **layout** — `Modifier.offset { IntOffset(...) }` + `.size(Dp)` — con
   letture di camera in **body-scope** del composable.
   Dimostrato su device: `TextNodeOverlay`/`ImageNodeOverlay` si muovono
   sempre e bene con pan/zoom, a ogni zoom, in **ogni** versione
   dell'app; l'`EraserPreview` (Canvas che "disegna in screen-space con
   letture body-scope") fa lo stesso; il tratto in corso (v1.0,
   "disegna in world + `graphicsLayer`") seguiva il dito.
2. **NON** affidabile (non aggiornava la camera su questa tablet; NON usare
   per i tratti):
   - `graphicsLayer` **block** dentro un composable (v1.1: sfaso)
   - `DrawScope.translate/scale` dentro una closure di disegno (v1.2:
     penna "lontano")
   - `graphicsLayer` **property-style** (v1.3: tratti "fermi")

**Regola operativa** per questo dispositivo: la camera entra nel
rendering **una sola volta, per layout**, e viene letta **nel body
del composable**; le closure di disegno iterano **dati precalcolati**,
mai stato. (Coerente anche con il "Perf" §4.4: il layer GPU non
rilette nulla quando la camera cambia, quindi si muove senza ricomposition.)

**Regola di consegna** (imparata su v1.0): se l'utente dice "sembra lo
stesso", verifica **`versionCode`** e nome APK diverso, e **verifica i
dex** (unzip + strings per tutti i `classes*.dex`) prima di parlare di
bug.

## 15. File di consegna

- **Un solo installabile in `~/`** (regola richiesta dall'utente):
  - attualmente **`/home/lorenzo/Zibaldone-v1.9.apk`** (~20 MB)
  - l'APK di build a valle: `app/build/outputs/apk/debug/app-debug.apk`
- Installazione: copia sulla tablet, tap, `Accetta` "install known
  source" (firma debug = stessa di tutte le versioni → sovrascrive).
