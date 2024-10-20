package ani.dantotsu.media.manga.mangareader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
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

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var activePointerId = -1
    private var isDragging = false
    private var initialDownY: Float = 0f
    private var initialMotionY: Float = 0f
    private var isVertical = true // Use a boolean for orientation

    // Public callbacks for swipe events
    var onTopSwiped: ((Float) -> Unit)? = null
    var onBottomSwiped: ((Float) -> Unit)? = null
    var onLeftSwiped: (() -> Unit)? = null
    var onRightSwiped: (() -> Unit)? = null

    private val scrollPosition: ScrollPosition
        get() {
            val child = this.child ?: return ScrollPosition.None
            val canScrollVertically = !child.canScrollVertically(-1) && !child.canScrollVertically(1)
            val canScrollHorizontally = !child.canScrollHorizontally(-1) && !child.canScrollHorizontally(1)
            return when {
                canScrollVertically && !canScrollHorizontally -> if (canScrollVertically(-1)) ScrollPosition.Start else ScrollPosition.End
                !canScrollVertically && canScrollHorizontally -> if (canScrollHorizontally(-1)) ScrollPosition.Start else ScrollPosition.End
                canScrollVertically && canScrollHorizontally -> ScrollPosition.Both
                else -> ScrollPosition.None
            }
        }

    private enum class ScrollPosition {
        None, Start, End, Both
    }

    private fun handlePointerUp(event: MotionEvent) {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        if (pointerId == activePointerId) {
            activePointerId = if (pointerIndex == 0) 1 else 0
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || scrollPosition == ScrollPosition.None) {
            return false
        }

        val action = event.actionMasked
        val pointerIndex: Int

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                isDragging = false
                pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) {
                    return false
                }
                initialDownY = if (isVertical) event.getY(pointerIndex) else event.getX(pointerIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == -1) {
                    return false
                }
                pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) {
                    return false
                }
                val currentY = if (isVertical) event.getY(pointerIndex) else event.getX(pointerIndex)
                startDragging(currentY)
            }

            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                activePointerId = -1
            }
        }
        return isDragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || scrollPosition == ScrollPosition.None) {
            return false
        }

        val action = event.actionMasked
        val pointerIndex: Int

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val currentY = if (isVertical) event.getY(pointerIndex) else event.getX(pointerIndex)
                startDragging(currentY)

                if (!isDragging) return false

                val overscroll = getDiff(currentY) * DRAG_RATE
                if (abs(overscroll) <= 0) return false

                parent.requestDisallowInterceptTouchEvent(true)

                if (isVertical) {
                    val dragDistance =
                        Resources.getSystem().displayMetrics.heightPixels / dragDivider
                    performSwiping(overscroll, dragDistance, onTopSwiped, onBottomSwiped)
                } else {
                    val dragDistance =
                        Resources.getSystem().displayMetrics.widthPixels / dragDivider
                    performSwiping(overscroll, dragDistance, onLeftSwiped, onRightSwiped)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerIndex = event.actionIndex
                if (pointerIndex < 0) {
                    return false
                }
                activePointerId = event.getPointerId(pointerIndex)
            }

            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP -> {
                if (isVertical) {
                    onTopSwiped?.invoke(0f)
                    onBottomSwiped?.invoke(0f)
                } else {
                    onRightSwiped?.invoke(0f)
                    onLeftSwiped?.invoke(0f)
                }
                pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) {
                    return false
                }
                if (isDragging) {
                    val currentY = if (isVertical) event.getY(pointerIndex) else event.getX(pointerIndex)
                    val overscroll = getDiff(currentY) * DRAG_RATE
                    isDragging = false
                    finishSpinner(overscroll)
                }
                activePointerId = -1
                return false
            }

            MotionEvent.ACTION_CANCEL -> return false
        }
        return true
    }

    private fun getDiff(pos: Float) = when (scrollPosition) {
        ScrollPosition.None -> 0f
        ScrollPosition.Start, ScrollPosition.Both -> pos - initialMotionY
        ScrollPosition.End -> initialMotionY - pos
    }

    private fun startDragging(pos: Float) {
        val posDiff = abs(getDiff(pos))
        if (posDiff > touchSlop && !isDragging) {
            initialMotionY = initialDownY + touchSlop
            isDragging = true
        }
    }

    private fun performSwiping(
        overscrollDistance: Float,
        totalDragDistance: Int,
        startBlock: (Float) -> Unit?,
        endBlock: (Float) -> Unit?
    ) {
        val distance = overscrollDistance * 2 / totalDragDistance
        when (scrollPosition) {
            ScrollPosition.Start -> startBlock?.invoke(distance)
            ScrollPosition.End -> endBlock?.invoke(distance)
            ScrollPosition.Both -> {
                startBlock?.invoke(distance)
                endBlock?.invoke(-distance)
            }
            else -> {}
        }
    }

    private fun performSwipe(
        overscrollDistance: Float,
        totalDragDistance: Int,
        startBlock: () -> Unit?,
        endBlock: () -> Unit?
    ) {
        fun check(distance: Float, block: () -> Unit?) {
            if (distance * 2 > totalDragDistance) {
                block?.invoke()
            }
        }
        when (scrollPosition) {
            ScrollPosition.Start -> check(overscrollDistance) { startBlock?.invoke() }
            ScrollPosition.End -> check(overscrollDistance) { endBlock?.invoke() }
            ScrollPosition.Both -> {
                check(overscrollDistance) { startBlock?.invoke() }
                check(-overscrollDistance) { endBlock?.invoke() }
            }

            else -> {}
        }
    }

    private fun finishSpinner(overscrollDistance: Float) {
        if (isVertical) {
            val totalDragDistance = Resources.getSystem().displayMetrics.heightPixels / dragDivider
            performSwipe(overscrollDistance, totalDragDistance, onTopSwiped, onBottomSwiped)
        } else {
            val totalDragDistance = Resources.getSystem().displayMetrics.widthPixels / dragDivider
            performSwipe(overscrollDistance, totalDragDistance, onLeftSwiped, onRightSwiped)
        }
    }
}
