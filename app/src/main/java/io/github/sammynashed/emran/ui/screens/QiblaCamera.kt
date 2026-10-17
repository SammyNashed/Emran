package io.github.sammynashed.emran.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sammynashed.emran.R
import kotlin.math.abs

/**
 * A live camera picture and nothing else.
 *
 * It owns a PREVIEW-ONLY capture session: there is no image reader, no recorder and no file
 * anywhere in this class, so nothing the camera sees can be captured, kept or sent. When the
 * screen goes away the camera is released.
 */
@Composable
fun CameraFeed(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds()) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val controller = remember { CameraController(context) }
        // Pick the camera size whose portrait shape is closest to the screen's, then scale it to
        // cover the screen (centre-cropped), so the picture is never stretched.
        val size = remember(viewW, viewH) { controller.chooseSize(viewH / viewW) }
        val aspect = size.height.toFloat() / size.width.toFloat() // portrait: width / height
        val w = maxOf(viewW, viewH * aspect)
        val h = w / aspect
        DisposableEffect(Unit) { onDispose { controller.close() } }
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            controller.start(st, size)
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            controller.close()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() }),
        )
    }
}

private class CameraController(private val context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val main = Handler(Looper.getMainLooper())
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var closed = false

    private val cameraId: String? = runCatching {
        manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    /** Largest preview size (up to 1080p) whose portrait aspect (short/long side) is closest to [portraitAspectInv]. */
    fun chooseSize(viewHeightOverWidth: Float): Size {
        val fallback = Size(1280, 720)
        val id = cameraId ?: return fallback
        val sizes = runCatching {
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)?.toList()
        }.getOrNull().orEmpty().filter { it.width <= 1920 && it.height <= 1080 }
        if (sizes.isEmpty()) return fallback
        // Camera sizes are landscape (width > height); on screen that is height / width.
        val best = sizes.minOf { abs(it.width.toFloat() / it.height - viewHeightOverWidth) }
        return sizes.filter { abs(it.width.toFloat() / it.height - viewHeightOverWidth) <= best + 0.02f }
            .maxBy { it.width * it.height }
    }

    @SuppressLint("MissingPermission")
    fun start(texture: SurfaceTexture, size: Size) {
        val id = cameraId ?: return
        closed = false
        texture.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(texture)
        runCatching {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (closed) { camera.close(); return }
                    device = camera
                    runCatching {
                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                if (closed) { s.close(); return }
                                session = s
                                val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(surface)
                                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                }
                                runCatching { s.setRepeatingRequest(req.build(), null, main) }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {}
                        }, main)
                    }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, main)
        }
    }

    fun close() {
        closed = true
        runCatching { session?.close() }
        runCatching { device?.close() }
        session = null
        device = null
    }
}

/**
 * The Qibla placed in the room you are looking at.
 *
 * Hold the phone upright and look around: the Kaaba hangs in the air in the Qibla's direction
 * and a prayer mat lies on the floor under it, pointing that way. Past the edge of the picture a
 * chevron says which way to turn. Everything is drawn flat; the camera is only the backdrop.
 */
@Composable
fun QiblaArOverlay(q: QiblaReading, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val fov = 56f // the phone camera's horizontal field of view, portrait
        val turn = if (q.ready && q.upright) q.turn else null
        if (turn != null) {
            val x = w / 2f + turn / fov * w
            val visible = abs(turn) < fov / 2f + 6f
            if (visible) {
                val lit = q.facing
                // Beam: from the Kaaba in the air down to the mat on the floor.
                Canvas(Modifier.fillMaxSize()) {
                    val top = h * 0.27f
                    val bottom = h * 0.50f
                    drawLine(
                        Color.White.copy(alpha = if (lit) 0.95f else 0.55f),
                        Offset(x, top + 26.dp.toPx()), Offset(x, bottom),
                        strokeWidth = (if (lit) 3f else 2f).dp.toPx(),
                    )
                    drawKaaba(Offset(x, top), 46.dp.toPx(), Color.White)
                }
                val matW = 104.dp
                val matH = 214.dp
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = with(density) { x.toDp() } - matW / 2,
                            y = with(density) { (h * 0.50f).toDp() } - 10.dp,
                        )
                        .size(matW, matH)
                        // Lie flat on the floor: tipped back, with the top (the mihrab) farthest away.
                        .graphicsLayer {
                            rotationX = 64f
                            cameraDistance = 14f * density.density
                            val s = if (lit) 1.06f else 1f
                            scaleX = s; scaleY = s
                        },
                ) {
                    Image(painterResource(R.drawable.prayer_mat), null, Modifier.fillMaxSize())
                }
            } else {
                // Off the side of the picture: a chevron on that edge.
                val left = turn < 0
                Canvas(Modifier.fillMaxSize()) {
                    val cx = if (left) 34.dp.toPx() else size.width - 34.dp.toPx()
                    val cy = size.height * 0.45f
                    val d = if (left) 1f else -1f
                    val a = 12.dp.toPx()
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx + a * d, cy - 2.2f * a)
                        lineTo(cx - a * d, cy)
                        lineTo(cx + a * d, cy + 2.2f * a)
                    }
                    drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
    }
}
