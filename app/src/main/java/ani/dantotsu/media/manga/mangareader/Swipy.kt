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

    var dragThreshold = 0.2f
    var isVertical = true

    private var activeChild: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Callbacks with original names to match MangaReaderActivity
    var topBeingSwiped: ((Float) -> Unit) = {}
    var onTopSwiped: (() -> Unit) = {}
    var bottomBeingSwiped: ((Float) -> Unit) = {}
    var onBottomSwiped: (() -> Unit) = {}
    var leftBeingSwiped: ((Float) -> Unit) = {}
    var onLeftSwiped: (() -> Unit) = {}
    var rightBeingSwiped: ((Float) -> Unit) = {}
    var onRightSwiped: (() -> Unit) = {}

    // Child property to match original code
    var child: View?
        get() = activeChild
        set(value) {
            activeChild = value
        }

    private sealed class ScrollState {
        object None : ScrollState()
        object Start : ScrollState()
        object End : ScrollState()
    }

    private var scrollState: ScrollState = ScrollState.None

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

                val delta = if (isVertical) {
                    ev.y - initialTouchY
                } else {
                    ev.x - initialTouchX
                }
                
                if (abs(delta) > touchSlop) {
                    isDragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
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
                if (isVertical) topBeingSwiped.invoke(abs(progress))
                else leftBeingSwiped.invoke(abs(progress))
            }
            ScrollState.End -> {
                if (isVertical) bottomBeingSwiped.invoke(abs(progress))
                else rightBeingSwiped.invoke(abs(progress))
            }
            ScrollState.None -> {}
        }
    }

    private fun handleSwipeComplete(progress: Float) {
        if (abs(progress) >= 1f) {
            when (scrollState) {
                ScrollState.Start -> {
                    if (isVertical) onTopSwiped.invoke()
                    else onLeftSwiped.invoke()
                }
                ScrollState.End -> {
                    if (isVertical) onBottomSwiped.invoke()
                    else onRightSwiped.invoke()
                }
                ScrollState.None -> {}
            }
        }

        // Reset progress
        handleSwipeProgress(0f)
    }
}
