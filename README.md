# Zibaldone

App Android per creare moodboard su un **piano infinito**: disegno a mano libera,
note di testo e foto di riferimento, tutto su una tela che si sposta e si ingrandisce
con le dita.

Lo *zibaldone* è il quaderno in cui si accumulano appunti, ritagli e pensieri sparsi
senza un ordine prestabilito — un moodboard di carta.

## Cosa fa

- **Tre attrezzi**: Seleziona, Penna (spessore 2–24 dp), Gomma con raggio regolabile
  (12–64 dp) che erode *solo la porzione di tratto toccata*, non l'intero tratto.
- **Note di testo** selezionabili, spostabili, ridimensionabili; doppio tocco per
  modificarle in linea.
- **Foto** dalla galleria: spostabili, ridimensionabili dai quattro angoli e
  **ruotabili** (doppio tocco → frecce curve, con aggancio a 0/90/180/270°).
- **Pan e zoom a due dita** ancorati al centro della pinch (0.1×–10×).
- **Export/import portabile** in un singolo file `.zib`: le foto viaggiano dentro il
  pacchetto, quindi una board si apre su qualsiasi dispositivo con l'app.

## Compilare

Richiede JDK 17 e l'SDK Android (platform 34, build-tools 34.0.0).

```bash
export JAVA_HOME=/percorso/del/jdk-17
export ANDROID_HOME=$HOME/android-sdk
./gradlew :app:assembleDebug
```

L'APK finisce in `app/build/outputs/apk/debug/app-debug.apk`.

Lo stile del codice è verificato con ktlint:

```bash
./gradlew :app:ktlintCheck    # controlla
./gradlew :app:ktlintFormat   # corregge il correggibile
```

## Struttura

Modulo singolo `:app`, interamente Jetpack Compose + Material 3. Nessun database:
lo stato è un modello serializzabile puro (`kotlinx.serialization`).

| Percorso | Ruolo |
|---|---|
| `model/` | `BoardElement` (sealed: Drawing / TextNode / ImageNode) e generazione degli id |
| `ui/BoardScreen.kt` | Scaffold, barre, FAB, ordine dei livelli |
| `ui/BoardGesture.kt` | **Dispatcher unico** di tutto l'input a puntatore |
| `ui/HitTesting.kt` | Hit testing manuale in spazio-schermo |
| `ui/*Overlay.kt`, `ui/StrokeCanvas.kt` | Rendering di note, foto e tratti |
| `util/CanvasMath.kt` | Matematica mondo↔schermo, taglio dei tratti per la gomma |
| `util/ExportImportManager.kt` | Formato `.zib` (ZIP + manifest) |
| `view/` | `BoardViewModel` e lo stato della camera |

Due vincoli che non sono evidenti dal codice:

1. **La camera entra nel rendering solo attraverso il layout** (`Modifier.offset {}` /
   `.size()`), letta nel *body* del composable. Applicarla dentro una closure di
   disegno o via `graphicsLayer` è stato provato e non funziona sul tablet di
   riferimento.
2. **Tutte le gesture stanno in `BoardGesture.kt`**: i nodi non hanno gestori propri.

## Documentazione

[`PROGETTO.md`](PROGETTO.md) è il diario di progetto: architettura, cronologia delle
versioni con sintomi e cause di ogni bug risolto, lezioni di compilazione e limiti
noti. È in italiano ed è la fonte autorevole; i commenti nel codice sono in inglese.
