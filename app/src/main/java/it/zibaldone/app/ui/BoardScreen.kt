package it.zibaldone.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.zibaldone.app.R
import it.zibaldone.app.model.BoardElement
import it.zibaldone.app.view.BoardTool
import it.zibaldone.app.view.BoardViewModel

/**
 * Main screen of Zibaldone.
 *
 * Layer structure (bottom -> top):
 *  1. WHITE background
 *  2. STROKE LAYER: every stroke is its OWN node, placed and sized with
 *     layout modifiers (`offset` + `size`) from camera values read in the
 *     node's composable body — the same mechanism as the notes and photos.
 *     No `graphicsLayer` and no camera state inside a draw closure: both
 *     were tried (v1.1-v1.3) and failed to track the camera on the target
 *     tablet. The single pointer dispatcher (boardDispatch) is attached here.
 *  3. NODE LAYER: text notes and image nodes, each positioned/sized from
 *     the current camera in its OWN composable scope.
 *  4. SELECTION overlay (stroke bounding box) and the ERASER PREVIEW ring,
 *     both small self-contained canvases.
 *
 * Nothing outside the affected node/layer reads the camera state, so a
 * pan/zoom frame never recomposes the screen or re-records the strokes
 * canvas: that is the fix for both the "scatta" jank and the disappearing
 * elements (the zoom is also anchored on the pinch centroid).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(viewModel: BoardViewModel = viewModel()) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val densityPx = density.density

    var showClearDialog by remember { mutableStateOf(false) }

    // The MEASURED board area, not the whole screen. Pointer coordinates and
    // node offsets live in this box's space, and it is what is left after the
    // top app bar and the bottom bar (easily 150+ dp together): sizing CENTRA
    // and "insert at the centre" from the screen made the fit overshoot
    // vertically and dropped new notes below the visible middle.
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun canvasCenterPx(): Offset = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.saveBoard(context, uri)
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                viewModel.loadBoard(context, uri)
            }
        }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                viewModel.addImageFromUri(context, uri, canvasCenterPx())
            }
        }

    val selectedId = viewModel.selectedId
    val selectedElement =
        if (selectedId != null) {
            viewModel.elements.find { it.id == selectedId }
        } else {
            null
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { showClearDialog = true }) {
                        Text("Svuota")
                    }
                    TextButton(
                        enabled = selectedId != null,
                        onClick = {
                            val id = selectedId
                            if (id != null) viewModel.removeElement(id)
                        }
                    ) {
                        Text("Elimina")
                    }
                    TextButton(
                        enabled = canvasSize != IntSize.Zero,
                        onClick = {
                            viewModel.fitToContent(
                                widthPx = canvasSize.width.toFloat(),
                                heightPx = canvasSize.height.toFloat(),
                                densityPx = densityPx
                            )
                        }
                    ) {
                        Text("Centra")
                    }
                    TextButton(onClick = { viewModel.resetView() }) {
                        Text("Azzera")
                    }
                    TextButton(onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    }) {
                        Text("Importa")
                    }
                    TextButton(onClick = {
                        exportLauncher.launch("board.zib")
                    }) {
                        Text("Esporta")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolButton(
                            label = "Seleziona",
                            active = viewModel.tool == BoardTool.SELECT
                        ) { viewModel.tool = BoardTool.SELECT }
                        ToolButton(
                            label = "Penna",
                            active = viewModel.tool == BoardTool.PEN
                        ) { viewModel.tool = BoardTool.PEN }
                        ToolButton(
                            label = "Gomma",
                            active = viewModel.tool == BoardTool.ERASER
                        ) { viewModel.tool = BoardTool.ERASER }
                    }
                    when (viewModel.tool) {
                        BoardTool.PEN ->
                            SizeSlider(
                                label = "Spessore penna",
                                value = viewModel.penWidthDp,
                                range = 2f..24f
                            ) { viewModel.penWidthDp = it }

                        BoardTool.ERASER ->
                            SizeSlider(
                                label = "Dimensione gomma",
                                value = viewModel.eraserRadiusDp,
                                range = 12f..64f
                            ) { viewModel.eraserRadiusDp = it }

                        BoardTool.SELECT ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                            ) {
                                Text(
                                    text =
                                        when {
                                            selectedElement == null ->
                                                "Tocca un elemento per selezionarlo · Trascina per spostarlo · " +
                                                    "Doppio tocco: modifica una nota, ruota una foto"
                                            viewModel.rotatingId == selectedElement.id ->
                                                "Trascina una freccia per ruotare · Si aggancia a 0/90/180/270° · " +
                                                    "Tocca altrove per uscire"
                                            selectedElement is BoardElement.ImageNode ->
                                                "Maniglie ai quattro angoli: ridimensiona · " +
                                                    "Doppio tocco: ruota · Elimina in alto: rimuovi"
                                            else ->
                                                "Maniglie ai quattro angoli: ridimensiona · " +
                                                    "Doppio tocco: modifica · Elimina in alto: rimuovi"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                    }
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                FloatingActionButton(
                    onClick = { imagePicker.launch(arrayOf("image/*")) }
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = "Aggiungi foto")
                }
                Spacer(modifier = Modifier.height(8.dp))
                FloatingActionButton(
                    onClick = {
                        val worldCenter = viewModel.screenToWorld(canvasCenterPx())
                        viewModel.addText("Nuova nota", worldCenter)
                    }
                ) {
                    Icon(Icons.Filled.Title, contentDescription = "Aggiungi nota")
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .background(Color.White)
        ) {
            // ------------------------------------------------------------------
            // 2. STROKES + GESTURES — one finger = tool, two fingers =
            //    camera (anchored pinch). Every stroke is a self-positioned
            //    node (offset/size layout, exactly like notes and photos,
            //    which track the camera perfectly on device); the gesture
            //    dispatcher sits on the same full-size surface.
            // ------------------------------------------------------------------
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            boardDispatch(vm = viewModel, densityPx = densityPx)
                        }
            ) {
                StrokesCanvas(viewModel)
            }

            // ------------------------------------------------------------------
            // 3. NODE LAYER — text notes and image nodes
            // ------------------------------------------------------------------
            for (element in viewModel.elements) {
                when (element) {
                    is BoardElement.TextNode ->
                        TextNodeOverlay(
                            element = element,
                            camera = viewModel.camera,
                            isSelected = element.id == viewModel.selectedId,
                            isEditing = element.id == viewModel.editingTextId,
                            onTextChange = {
                                viewModel.updateText(element.id, it, element.fontSize)
                            },
                            onEditingDone = { viewModel.stopEditingText() }
                        )

                    is BoardElement.ImageNode ->
                        ImageNodeOverlay(
                            element = element,
                            camera = viewModel.camera,
                            isSelected = element.id == viewModel.selectedId,
                            isRotating = element.id == viewModel.rotatingId
                        )

                    is BoardElement.Drawing -> Unit
                }
            }

            // ------------------------------------------------------------------
            // 4. SELECTION overlay + ERASER preview
            // ------------------------------------------------------------------
            if (selectedElement is BoardElement.Drawing) {
                StrokeSelectionBox(selectedElement, viewModel.camera, density)
            }

            EraserPreview(viewModel)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Svuotare la board?") },
            text = {
                Text(
                    "Tutti gli elementi verranno rimossi. " +
                        "Esporta prima la board se vuoi conservarla."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearBoard()
                        showClearDialog = false
                    }
                ) { Text("Svuota") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annulla") }
            }
        )
    }
}

/** A tool toggle in the bottom bar: filled when active. */
@Composable
private fun ToolButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    if (active) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

/** The size row shown under the tool bar for PEN / ERASER. */
@Composable
private fun SizeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(110.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
    }
}
