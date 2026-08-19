package com.formula2latex.ui.main

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.formula2latex.data.image.DrawingExporter
import com.formula2latex.data.image.InkPoint
import com.formula2latex.data.image.InkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrawingEditor {
    val strokes = mutableStateListOf<InkStroke>()
    val redoStack = mutableStateListOf<InkStroke>()
    var eraser by mutableStateOf(false)
    var width by mutableFloatStateOf(6f)
    var canvasSize by mutableStateOf(Size.Zero)
    fun undo() { if (strokes.isNotEmpty()) redoStack += strokes.removeAt(strokes.lastIndex) }
    fun redo() { if (redoStack.isNotEmpty()) strokes += redoStack.removeAt(redoStack.lastIndex) }
    fun clear() { strokes.clear(); redoStack.clear() }
}

@Composable
fun DrawingPad(
    editor: DrawingEditor,
    canvasHeight: Dp = 360.dp,
    onConvert: (ByteArray) -> Unit,
) {
    val current = remember { mutableStateListOf<InkPoint>() }
    var currentEraser by remember { mutableStateOf(false) }
    var currentWidth by remember { mutableFloatStateOf(editor.width) }
    var error by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var stylusButtonHeld by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun commitCurrent() {
        if (current.isEmpty()) return
        editor.strokes += InkStroke(current.toList(), currentWidth, currentEraser)
        editor.redoStack.clear()
        current.clear()
    }

    fun addPoint(x: Float, y: Float) {
        if (x < 0f || y < 0f || x > editor.canvasSize.width || y > editor.canvasSize.height) return
        val previous = current.lastOrNull()
        if (previous == null || kotlin.math.abs(previous.x - x) > 0.01f || kotlin.math.abs(previous.y - y) > 0.01f) {
            current += InkPoint(x, y)
        }
    }

    fun temporaryEraser(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        if (tool == MotionEvent.TOOL_TYPE_ERASER) return true
        if (tool != MotionEvent.TOOL_TYPE_STYLUS) return false
        val stylusButtons = MotionEvent.BUTTON_STYLUS_PRIMARY or
            MotionEvent.BUTTON_STYLUS_SECONDARY or MotionEvent.BUTTON_SECONDARY
        return event.buttonState and stylusButtons != 0
    }

    fun updateStrokeMode(erase: Boolean, x: Float, y: Float) {
        if (current.isNotEmpty() && erase != currentEraser) {
            commitCurrent()
            currentEraser = erase
            currentWidth = editor.width
            addPoint(x, y)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = editor::undo, enabled = editor.strokes.isNotEmpty()) { Text("Undo") }
            TextButton(onClick = editor::redo, enabled = editor.redoStack.isNotEmpty()) { Text("Redo") }
            FilterChip(selected = editor.eraser, onClick = { editor.eraser = !editor.eraser }, label = { Text("Eraser") })
            IconButton(onClick = editor::clear) { Icon(Icons.Default.Clear, "Clear drawing") }
        }
        Row {
            Icon(Icons.Default.Edit, "Pen width")
            Slider(editor.width, { editor.width = it }, valueRange = 2f..18f, modifier = Modifier.weight(1f))
        }
        Canvas(
            Modifier.fillMaxWidth().height(canvasHeight).background(Color.White).clipToBounds()
                .onSizeChanged { editor.canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .motionEventSpy { event ->
                    if (event.pointerCount > 0) stylusButtonHeld = temporaryEraser(event)
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        current.clear()
                        currentEraser = editor.eraser || stylusButtonHeld
                        currentWidth = editor.width
                        addPoint(down.position.x, down.position.y)
                        down.consume()

                        var pressed = true
                        var multiTouch = false
                        val primaryIsStylus = down.type == PointerType.Stylus
                        while (pressed) {
                            val pointerEvent = awaitPointerEvent()
                            val change = pointerEvent.changes.firstOrNull { it.id == down.id } ?: break
                            pressed = change.pressed
                            if (!primaryIsStylus && pointerEvent.changes.count { it.pressed } > 1) {
                                multiTouch = true
                                current.clear()
                            }
                            if (multiTouch) continue
                            if (pressed) {
                                updateStrokeMode(
                                    editor.eraser || stylusButtonHeld,
                                    change.position.x,
                                    change.position.y,
                                )
                            }
                            change.historical.forEach { point ->
                                addPoint(point.position.x, point.position.y)
                            }
                            addPoint(change.position.x, change.position.y)
                            change.consume()
                        }
                        if (multiTouch) current.clear() else commitCurrent()
                    }
                },
        ) {
            fun draw(stroke: InkStroke) {
                if (stroke.points.isEmpty()) return
                val color = if (stroke.eraser) Color.White else Color.Black
                if (stroke.points.size == 1) {
                    val point = stroke.points.first()
                    drawCircle(color, stroke.width / 2f, Offset(point.x, point.y))
                    return
                }
                val path = Path().apply {
                    moveTo(stroke.points.first().x, stroke.points.first().y)
                    stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path,
                    color,
                    style = Stroke(stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            editor.strokes.forEach(::draw)
            if (current.isNotEmpty()) draw(InkStroke(current, currentWidth, currentEraser))
        }
        Text(
            "Tip: hold the S Pen side button to erase temporarily. Two-finger touches do not draw.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        Button(
            enabled = !exporting && editor.strokes.any { !it.eraser && it.points.isNotEmpty() },
            onClick = {
                val snapshot = editor.strokes.toList()
                val size = editor.canvasSize
                exporting = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.Default) {
                            DrawingExporter.export(snapshot, size.width, size.height)
                        }
                    }.onSuccess {
                        error = null
                        onConvert(it)
                    }.onFailure {
                        error = it.message ?: "The drawing could not be exported."
                    }
                    exporting = false
                }
            },
        ) { Text(if (exporting) "Preparing drawing…" else "Convert drawing") }
    }
}
