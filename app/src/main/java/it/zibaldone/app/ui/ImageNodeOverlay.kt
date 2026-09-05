package it.zibaldone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.zibaldone.app.model.BoardElement
import it.zibaldone.app.view.BoardViewModel
import it.zibaldone.app.view.CameraState
import java.io.File
import kotlin.math.roundToInt

/**
 * A reference image node in board space.
 *
 * It is NON-interactive: selection, moving, rescaling and deletion are all
 * handled by the board dispatcher (select it, drag it, use the bottom
 * handle to resize, "Elimina" to delete).
 *
 * Size: 200 dp * node scale, multiplied by the board zoom, so the node
 * always keeps its true size both visually and in hit testing.
 * The camera is read in this node's own composable scope.
 */
@Composable
fun ImageNodeOverlay(
    element: BoardElement.ImageNode,
    camera: CameraState,
    isSelected: Boolean,
    isRotating: Boolean
) {
    val context = LocalContext.current
    val zoom = camera.zoom
    val top = camera.worldToScreen(Offset(element.position.x, element.position.y))
    val nodeSize = (BoardViewModel.IMAGE_BASE_DP.dp * element.scale * zoom)
    val offsetModifier =
        Modifier
            .offset { IntOffset(top.x.roundToInt(), top.y.roundToInt()) }
            .size(nodeSize)

    // The PHOTO carries the rotation; the selection frame and the handles are
    // a SEPARATE, unrotated sibling placed on the same layout box. Keeping the
    // controls axis-aligned is what lets hit testing stay a plain rectangle
    // check — and `rotate` only affects drawing, so the camera still enters
    // this node exclusively through offset/size (layout), as the device
    // requires.
    Box(
        modifier =
            offsetModifier
                .rotate(element.rotation)
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = Color(0x55000000),
                    shape = RoundedCornerShape(8.dp)
                )
    ) {
        AsyncImage(
            model =
                remember(element.imageUri) {
                    val imageData =
                        if (element.imageUri.startsWith("content://")) {
                            element.imageUri
                        } else {
                            File(element.imageUri)
                        }
                    ImageRequest.Builder(context)
                        .data(imageData)
                        .crossfade(true)
                        .build()
                },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .padding(2.dp)
                    .fillMaxSize()
        )
    }

    if (isSelected) {
        Box(
            modifier =
                offsetModifier
                    .border(2.dp, SelectionBlue, RoundedCornerShape(8.dp))
        ) {
            if (isRotating) {
                RotationHandles()
            } else {
                SelectionHandles()
            }
        }
    }
}
