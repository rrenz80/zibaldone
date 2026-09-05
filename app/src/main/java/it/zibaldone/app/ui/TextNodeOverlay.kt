package it.zibaldone.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.zibaldone.app.model.BoardElement
import it.zibaldone.app.view.CameraState
import kotlin.math.roundToInt

/**
 * A text note in board space.
 *
 * Design goals (per the UX spec):
 *  - by default it is NON-interactive: the board dispatcher decides
 *    selection, moving and text editing, so a tap never lands in a
 *    TextField by accident;
 *  - when selected: highlight border + resize handle (bottom-right);
 *  - when being edited (double tap): an inline input field appears; the
 *    IME "Fatto" action or a tap on another element confirms the editing
 *    and the selection can be transferred to another element.
 *
 * It reads the camera in its OWN composable scope, so during a camera
 * frame only this small node recomposes — the screen and the strokes
 * canvas stay untouched.
 */
@Composable
fun TextNodeOverlay(
    element: BoardElement.TextNode,
    camera: CameraState,
    isSelected: Boolean,
    isEditing: Boolean,
    onTextChange: (text: String) -> Unit,
    onEditingDone: () -> Unit
) {
    val zoom = camera.zoom
    val top = camera.worldToScreen(Offset(element.position.x, element.position.y))

    Box(
        modifier =
            Modifier
                .offset { IntOffset(top.x.roundToInt(), top.y.roundToInt()) }
                .width(220.dp * zoom)
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) SelectionBlue else Color(0x44000000),
                    shape = RoundedCornerShape(6.dp)
                )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = element.text,
                color = Color(element.color),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (element.fontSize * zoom).sp,
                        lineHeight = ((element.fontSize * zoom) * 1.4f).sp
                    ),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp)
            )
            if (isEditing) {
                TextEditField(
                    value = element.text,
                    onValueChange = { onTextChange(it) },
                    onDone = onEditingDone
                )
            }
        }
        if (isSelected && !isEditing) {
            SelectionHandles()
        }
    }
}

/**
 * Inline single-line editor shown while a note is being edited.
 * It grabs focus when it appears; the IME Done action confirms.
 */
@Composable
private fun TextEditField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
                .focusRequester(focusRequester),
        shape = RoundedCornerShape(0.dp),
        keyboardOptions =
            KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Text
            ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = SelectionBlue
            )
    )
}
