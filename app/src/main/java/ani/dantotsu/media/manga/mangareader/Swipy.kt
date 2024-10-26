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
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val displayMetrics = Resources.getSystem().displayMetrics
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    
    var dragDivider: Int = 5
    var vertical = true
    var child: View? = getChildAt(0)

    // Using the original naming to maintain compatibility
    var topBeingSwiped: ((Float) -> Unit) = {}
    var onTopSwiped: (() -> Unit) = {}
    var bottomBeingSwiped: ((Float) -> Unit) = {}
    var onBottomSwiped: (() -> Unit) = {}
    var leftBeingSwiped: ((Float) -> Unit) = {}
    var onLeftSwiped: (() -> Unit) = {}
    var rightBeingSwiped: ((Float) -> Unit) = {}
    var onRightSwiped: (() -> Unit) = {}

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var activePointerId = INVALID_POINTER
    
    private var swipeDirection: SwipeDirection = SwipeDirection.NONE
    
    private enum class SwipeDirection {
        TOP, BOTTOM, LEFT, RIGHT, NONE
    }

    private fun determineSwipeDirection(view: View?, touchY: Float) {
        if (view == null) return
        
        swipeDirection = when {
            vertical -> {
                when {
                    !view.canScrollVertically(-1) || 
                    (!view.canScrollVertically(1) && touchY < displayMetrics.heightPixels / 2) -> SwipeDirection.TOP
                    
                    !view.canScrollVertically(1) || 
                    (!view.canScrollVertically(-1) && touchY >= displayMetrics.heightPixels / 2) -> SwipeDirection.BOTTOM
                    
                    else -> SwipeDirection.NONE
                }
            }
            else -> {
                when {
                    !view.canScrollHorizontally(-1) -> SwipeDirection.LEFT
                    !view.canScrollHorizontally(1) -> SwipeDirection.RIGHT
                    else -> SwipeDirection.NONE
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                initialTouchX = event.x
                initialTouchY = event.y
                lastTouchX = initialTouchX
                lastTouchY = initialTouchY
                isDragging = false
                determineSwipeDirection(getChildAt(0), initialTouchY)
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                
                if (!isDragging) {
                    val dx = x - initialTouchX
                    val dy = y - initialTouchY
                    val distance = if (vertical) abs(dy) else abs(dx)
                    
                    if (distance > touchSlop && swipeDirection != SwipeDirection.NONE) {
                        isDragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (isDragging) {
                    val delta = if (vertical) y - lastTouchY else x - lastTouchX
                    handleDrag(delta)
                }

                lastTouchX = x
                lastTouchY = y
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    val distance = if (vertical) 
                        lastTouchY - initialTouchY else 
                        lastTouchX - initialTouchX
                    finishDrag(distance)
                }
                reset()
            }

            MotionEvent.ACTION_CANCEL -> reset()
        }

        return true
    }

    private fun handleDrag(delta: Float) {
        val progress = delta / (if (vertical) displayMetrics.heightPixels else displayMetrics.widthPixels)
        when (swipeDirection) {
            SwipeDirection.TOP -> topBeingSwiped.invoke(progress)
            SwipeDirection.BOTTOM -> bottomBeingSwiped.invoke(progress)
            SwipeDirection.LEFT -> leftBeingSwiped.invoke(progress)
            SwipeDirection.RIGHT -> rightBeingSwiped.invoke(progress)
            SwipeDirection.NONE -> {}
        }
    }

    private fun finishDrag(distance: Float) {
        val threshold = if (vertical) 
            displayMetrics.heightPixels / dragDivider else 
            displayMetrics.widthPixels / dragDivider

        if (abs(distance) > threshold) {
            when (swipeDirection) {
                SwipeDirection.TOP -> onTopSwiped.invoke()
                SwipeDirection.BOTTOM -> onBottomSwiped.invoke()
                SwipeDirection.LEFT -> onLeftSwiped.invoke()
                SwipeDirection.RIGHT -> onRightSwiped.invoke()
                SwipeDirection.NONE -> {}
            }
        }
    }

    private fun reset() {
        isDragging = false
        activePointerId = INVALID_POINTER
        swipeDirection = SwipeDirection.NONE
        topBeingSwiped.invoke(0f)
        bottomBeingSwiped.invoke(0f)
        leftBeingSwiped.invoke(0f)
        rightBeingSwiped.invoke(0f)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                initialTouchX = event.x
                initialTouchY = event.y
                lastTouchX = initialTouchX
                lastTouchY = initialTouchY
                isDragging = false
                determineSwipeDirection(getChildAt(0), initialTouchY)
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == INVALID_POINTER) return false
                
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false

                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val dx = x - initialTouchX
                val dy = y - initialTouchY
                val distance = if (vertical) abs(dy) else abs(dx)

                if (distance > touchSlop && swipeDirection != SwipeDirection.NONE) {
                    isDragging = true
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                reset()
            }
        }

        return isDragging
    }

    companion object {
        private const val INVALID_POINTER = -1
    }
}
