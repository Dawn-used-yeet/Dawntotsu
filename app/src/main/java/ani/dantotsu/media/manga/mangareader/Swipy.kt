package ani.dantotsu.media.manga.mangareader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout

class Swipy @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var dragDivider: Int = 5
    var vertical: Boolean = true

    var topBeingSwiped: (Float) -> Unit = {}
    var onTopSwiped: () -> Unit = {}
    var onBottomSwiped: () -> Unit = {}
    var bottomBeingSwiped: (Float) -> Unit = {}
    var onLeftSwiped: () -> Unit = {}
    var leftBeingSwiped: (Float) -> Unit = {}
    var onRightSwiped: () -> Unit = {}
    var rightBeingSwiped: (Float) -> Unit = {}

    private var child: View? = null
    private var touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var activePointerId: Int = INVALID_POINTER
    private var isBeingDragged: Boolean = false
    private var initialDown: Float = 0f
    private var initialMotion: Float = 0f

    private var verticalPos: VerticalPosition = VerticalPosition.None
    private var horizontalPos: HorizontalPosition = HorizontalPosition.None

    companion object {
        private const val DRAG_RATE = 0.5f
        private const val INVALID_POINTER = -1
    }

    enum class VerticalPosition {
        Top, None, Bottom
    }

    enum class HorizontalPosition {
        Left, None, Right
    }

    init {
        child = getChildAt(0)
    }

    private fun setChildPosition() {
        child?.let {
            if (vertical) {
                verticalPos = when {
                    !canScrollVertically(1) -> VerticalPosition.Bottom
                    !canScrollVertically(-1) -> VerticalPosition.Top
                    else -> VerticalPosition.None
                }
            } else {
                horizontalPos = when {
                    !canScrollHorizontally(1) -> HorizontalPosition.Right
                    !canScrollHorizontally(-1) -> HorizontalPosition.Left
                    else -> HorizontalPosition.None
                }
            }
        }
    }

    private fun canChildScroll(): Boolean {
        setChildPosition()
        return if (vertical) verticalPos == VerticalPosition.None else horizontalPos == HorizontalPosition.None
    }

    private fun onSecondaryPointerUp(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        if (pointerId == activePointerId) {
            activePointerId = if (pointerIndex == 0) ev.getPointerId(1) else ev.getPointerId(0)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || canChildScroll()) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                isBeingDragged = false
                initialDown = if (vertical) ev.getY(0) else ev.getX(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == INVALID_POINTER) return false
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false
                startDragging(if (vertical) ev.getY(pointerIndex) else ev.getX(pointerIndex))
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetDragState()
        }
        return isBeingDragged
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || canChildScroll()) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                isBeingDragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false
                val pos = if (vertical) ev.getY(pointerIndex) else ev.getX(pointerIndex)
                handleDrag(pos)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = ev.actionIndex
                if (pointerIndex >= 0) activePointerId = ev.getPointerId (pointerIndex)
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(ev)
            MotionEvent.ACTION_UP -> {
                if (isBeingDragged) {
                    val pos = if (vertical) ev.getY(0) else ev.getX(0)
                    finishSpinner(pos)
                }
                resetDragState()
            }
            MotionEvent.ACTION_CANCEL -> resetDragState()
        }
        return true
    }

    private fun startDragging(pos: Float) {
        val posDiff = if ((vertical && verticalPos == VerticalPosition.Top) || (!vertical && horizontalPos == HorizontalPosition.Left))
            pos - initialDown
        else
            initialDown - pos
        if (posDiff > touchSlop && !isBeingDragged) {
            initialMotion = initialDown + touchSlop
            isBeingDragged = true
        }
    }

    private fun handleDrag(pos: Float) {
        if (isBeingDragged) {
            val overscroll = (pos - initialMotion) * DRAG_RATE
            if (overscroll > 0) {
                parent.requestDisallowInterceptTouchEvent(true)
                if (vertical) {
                    val totalDragDistance = Resources.getSystem().displayMetrics.heightPixels / dragDivider
                    if (verticalPos == VerticalPosition.Top)
                        topBeingSwiped.invoke(overscroll * 2 / totalDragDistance)
                    else
                        bottomBeingSwiped.invoke(overscroll * 2 / totalDragDistance)
                } else {
                    val totalDragDistance = Resources.getSystem().displayMetrics.widthPixels / dragDivider
                    if (horizontalPos == HorizontalPosition.Left)
                        leftBeingSwiped.invoke(overscroll / totalDragDistance)
                    else
                        rightBeingSwiped.invoke(overscroll / totalDragDistance)
                }
            }
        }
    }

    private fun finishSpinner(overscrollDistance: Float) {
        if (vertical) {
            val totalDragDistance = Resources.getSystem().displayMetrics.heightPixels / dragDivider
            if (overscrollDistance * 2 > totalDragDistance)
                if (verticalPos == VerticalPosition.Top)
                    onTopSwiped.invoke()
                else
                    onBottomSwiped.invoke()
        } else {
            val totalDragDistance = Resources.getSystem().displayMetrics.widthPixels / dragDivider
            if (overscrollDistance > totalDragDistance)
                if (horizontalPos == HorizontalPosition.Left)
                    onLeftSwiped.invoke()
                else
                    onRightSwiped.invoke()
        }
    }

    private fun resetDragState() {
        isBeingDragged = false
        activePointerId = INVALID_POINTER
        if (vertical) {
            topBeingSwiped.invoke(0f)
            bottomBeingSwiped.invoke(0f)
        } else {
            rightBeingSwiped.invoke(0f)
            leftBeingSwiped.invoke(0f)
        }
    }
}
