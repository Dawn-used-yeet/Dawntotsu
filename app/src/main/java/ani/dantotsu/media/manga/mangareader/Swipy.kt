package ani.dantotsu.media.manga.mangareader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.absoluteValue

class Swipy @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var dragDivider: Int = 5
    var isVerticalSwipeEnabled: Boolean = true
    private var childView: View? = getChildAt(0)

    // Callbacks for swipe events
    var onTopSwiped: ((Float) -> Unit) = {}
    var onBottomSwiped: ((Float) -> Unit) = {}
    var onLeftSwiped: ((Float) -> Unit) = {}
    var onRightSwiped: ((Float) -> Unit) = {}
    var topBeingSwiped: ((Float) -> Unit) = {}
    var bottomBeingSwiped: ((Float) -> Unit) = {}
    var leftBeingSwiped: ((Float) -> Unit) = {}
    var rightBeingSwiped: ((Float) -> Unit) = {}

    companion object {
        private const val DRAG_RATE = 0.5f
        private const val INVALID_POINTER = -1
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var activePointerId = INVALID_POINTER
    private var isBeingDragged = false
    private var initialDownPosition = 0f
    private var initialMotionPosition = 0f

    private enum class ScrollPosition {
        None, Start, End, Both
    }

    private var scrollPosition = ScrollPosition.None

    private fun updateScrollPosition() {
        childView?.let { child ->
            val canScrollUp = if (isVerticalSwipeEnabled) !child.canScrollVertically(-1) else !child.canScrollHorizontally(-1)
            val canScrollDown = if (isVerticalSwipeEnabled) !child.canScrollVertically(1) else !child.canScrollHorizontally(1)

            scrollPosition = when {
                canScrollUp && !canScrollDown -> ScrollPosition.Start
                !canScrollUp && canScrollDown -> ScrollPosition.End
                canScrollUp && canScrollDown -> ScrollPosition.Both
                else -> ScrollPosition.None
            }
        }
    }

    private fun canChildScroll(): Boolean {
        updateScrollPosition()
        return scrollPosition == ScrollPosition.None
    }

    private fun handleSecondaryPointerUp(event: MotionEvent) {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        if (pointerId == activePointerId) {
            activePointerId = if (pointerIndex == 0) event.getPointerId(1) else event.getPointerId(0)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || canChildScroll()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                isBeingDragged = false
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                initialDownPosition = if (isVerticalSwipeEnabled) event.getY(pointerIndex) else event.getX(pointerIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == INVALID_POINTER) return false

                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val currentPosition = if (isVerticalSwipeEnabled) event.getY(pointerIndex) else event.getX(pointerIndex)
                startDragging(currentPosition)
            }

            MotionEvent.ACTION_POINTER_UP -> handleSecondaryPointerUp(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isBeingDragged = false
                activePointerId = INVALID_POINTER
            }
        }
        return isBeingDragged
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || canChildScroll()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                isBeingDragged = false
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val currentPosition = if (isVerticalSwipeEnabled) event.getY(pointerIndex) else event.getX(pointerIndex)
                startDragging(currentPosition)

                if (!isBeingDragged) return false

                val overscrollDistance = getDiff(currentPosition) * DRAG_RATE
                if (overscrollDistance.absoluteValue <= 0) return false

                parent.requestDisallowInterceptTouchEvent(true)

                val dragDistance = if (isVerticalSwipeEnabled) {
                    Resources.getSystem().displayMetrics.heightPixels / dragDivider
                } else {
                    Resources.getSystem().displayMetrics.widthPixels / dragDivider
                }

                performSwiping(overscrollDistance, dragDistance, topBeingSwiped, bottomBeingSwiped, leftBeingSwiped, rightBeingSwiped)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                if (pointerIndex < 0) return false

                activePointerId = event.getPointerId(pointerIndex)
            }

            MotionEvent.ACTION_POINTER_UP -> handleSecondaryPointerUp(event)
            MotionEvent.ACTION_UP -> {
                if (isVerticalSwipeEnabled) {
                    topBeingSwiped(0f)
                    bottomBeingSwiped(0f)
                } else {
                    rightBeingSwiped(0f)
                    leftBeingSwiped(0f)
                }

                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val currentPosition = if (isVerticalSwipeEnabled) event.getY(pointerIndex) else event.getX(pointerIndex)
                val overscrollDistance = getDiff(currentPosition) * DRAG_RATE
                isBeingDragged = false
                finishSpinner(overscrollDistance)
                activePointerId = INVALID_POINTER
                return false
            }

            MotionEvent.ACTION_CANCEL -> return false
        }
        return true
    }

    private fun getDiff(position: Float): Float {
        return when (scrollPosition) {
            ScrollPosition.None -> 0f
            ScrollPosition.Start, ScrollPosition.Both -> position - initialMotionPosition
            ScrollPosition.End -> initialMotionPosition - position
        }
    }

    private fun startDragging(position: Float) {
        val posDiff = getDiff(position).absoluteValue
        if (posDiff > touchSlop && !isBeingDragged) {
            initialMotionPosition = initialDownPosition + touchSlop
            isBeingDragged = true
        }
    }

    private fun performSwiping(
        overscrollDistance: Float,
        totalDragDistance: Int,
        startBlock: (Float) -> Unit,
        endBlock: (Float) -> Unit,
        leftBlock: (Float) -> Unit,
        rightBlock: (Float) -> Unit
    ) {
        val distance = overscrollDistance * 2 / totalDragDistance
        when (scrollPosition) {
            ScrollPosition.Start -> startBlock(distance)
            ScrollPosition.End -> endBlock(distance)
            ScrollPosition.Both -> {
                startBlock(distance)
                endBlock(-distance)
            }
            else -> {}
        }

        if (!isVerticalSwipeEnabled) {
            when (scrollPosition) {
                ScrollPosition.Start -> leftBlock(distance)
                ScrollPosition.End -> rightBlock(distance)
                ScrollPosition.Both -> {
                    leftBlock(distance)
                    rightBlock(-distance)
                }
                else -> {}
            }
        }
    }

    private fun performSwipe(
        overscrollDistance: Float,
        totalDragDistance: Int,
        startBlock: () -> Unit,
        endBlock: () -> Unit
    ) {
        fun check(distance: Float, block: () -> Unit) {
            if (distance * 2 > totalDragDistance)
                block.invoke()
        }
        when (scrollPosition) {
            ScrollPosition.Start -> check(overscrollDistance) { startBlock() }
            ScrollPosition.End -> check(overscrollDistance) { endBlock() }
            ScrollPosition.Both -> {
                check(overscrollDistance) { startBlock() }
                check(-overscrollDistance) { endBlock() }
            }
            else -> {}
        }
    }

    private fun finishSpinner(overscrollDistance: Float) {
        if (isVerticalSwipeEnabled) {
            val totalDragDistance = Resources.getSystem().displayMetrics.heightPixels / dragDivider
            performSwipe(overscrollDistance, totalDragDistance, onTopSwiped, onBottomSwiped)
        } else {
            val totalDragDistance = Resources.getSystem().displayMetrics.widthPixels / dragDivider
            performSwipe(overscrollDistance, totalDragDistance, onLeftSwiped, onRightSwiped)
        }
    }
}
