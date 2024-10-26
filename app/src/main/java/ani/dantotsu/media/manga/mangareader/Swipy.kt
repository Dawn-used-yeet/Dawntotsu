package ani.dantotsu.media.manga.mangareader

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class Swipy @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var dragThreshold = 0.2f // 20% of screen dimension
    var isVertical = true

    private var activeChild: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Callbacks
    var onSwipeTop: ((progress: Float) -> Unit)? = null
    var onSwipeBottom: ((progress: Float) -> Unit)? = null
    var onSwipeLeft: ((progress: Float) -> Unit)? = null
    var onSwipeRight: ((progress: Float) -> Unit)? = null

    var onSwipeTopComplete: (() -> Unit)? = null
    var onSwipeBottomComplete: (() -> Unit)? = null
    var onSwipeLeftComplete: (() -> Unit)? = null
    var onSwipeRightComplete: (() -> Unit)? = null

    private sealed class ScrollState {
        object None : ScrollState()
        object Start : ScrollState()
        object End : ScrollState()
    }

    private var scrollState = ScrollState.None

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                initialTouchX = ev.x
                initialTouchY = ev.y
                activeChild = getChildAt(0)
                updateScrollState()
            }
            MotionEvent.ACTION_MOVE -> {
                if (scrollState == ScrollState.None) return false

                val dx = ev.x - initialTouchX
                val dy = ev.y - initialTouchY
                
                if (isVertical) {
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                        isDragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                } else {
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        isDragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                activeChild = null
                scrollState = ScrollState.None
            }
        }

        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || activeChild == null) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                val delta = if (isVertical) {
                    event.y - initialTouchY
                } else {
                    event.x - initialTouchX
                }

                val screenSize = if (isVertical) height else width
                val progress = (delta / (screenSize * dragThreshold)).coerceIn(-1f, 1f)

                handleSwipeProgress(progress)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    val delta = if (isVertical) {
                        event.y - initialTouchY
                    } else {
                        event.x - initialTouchX
                    }

                    val screenSize = if (isVertical) height else width
                    val progress = delta / (screenSize * dragThreshold)

                    handleSwipeComplete(progress)
                }
                isDragging = false
                activeChild = null
                scrollState = ScrollState.None
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateScrollState() {
        activeChild?.let { child ->
            scrollState = when {
                isVertical -> when {
                    !child.canScrollVertically(-1) -> ScrollState.Start
                    !child.canScrollVertically(1) -> ScrollState.End
                    else -> ScrollState.None
                }
                else -> when {
                    !child.canScrollHorizontally(-1) -> ScrollState.Start
                    !child.canScrollHorizontally(1) -> ScrollState.End
                    else -> ScrollState.None
                }
            }
        }
    }

    private fun handleSwipeProgress(progress: Float) {
        when (scrollState) {
            ScrollState.Start -> {
                if (isVertical) onSwipeTop?.invoke(abs(progress))
                else onSwipeLeft?.invoke(abs(progress))
            }
            ScrollState.End -> {
                if (isVertical) onSwipeBottom?.invoke(abs(progress))
                else onSwipeRight?.invoke(abs(progress))
            }
            ScrollState.None -> {}
        }
    }

    private fun handleSwipeComplete(progress: Float) {
        if (abs(progress) >= 1f) {
            when (scrollState) {
                ScrollState.Start -> {
                    if (isVertical) onSwipeTopComplete?.invoke()
                    else onSwipeLeftComplete?.invoke()
                }
                ScrollState.End -> {
                    if (isVertical) onSwipeBottomComplete?.invoke()
                    else onSwipeRightComplete?.invoke()
                }
                ScrollState.None -> {}
            }
        }

        // Reset progress
        handleSwipeProgress(0f)
    }
}
