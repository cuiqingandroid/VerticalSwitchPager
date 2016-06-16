package txxia.com.verticalswitchpager;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下可滑动
 *
 * @author cuiqing
 */
public class ScrollViewContainer extends ViewGroup {

    /**
     * 滑动状态回调接口
     */
    public interface OnPageChangeListener {
        /**
         * 页面滚动的时候调用此方法
         *
         * @param position             当前位置0或者1
         * @param positionOffset       取值范围[0, 1)，百分比
         * @param positionOffsetPixels 滑动的距离，像素
         */
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels);

        /**
         * 切换上下页面的时候调用此方法
         *
         * @param position 即将滑动到的页面0或者1
         */
        public void onPageSelected(int position);

    }

    private static final boolean DEBUG = true;
    private static final String TAG = "ScrollViewContainer";

    private static final int MIN_FLING_VELOCITY = 400; // dps
    private static final int MIN_DISTANCE_FOR_FLING = 25; // dips
    private static final int MAX_SETTLE_DURATION = 600; // ms
    private static final int DEFAULT_GUTTER_SIZE = 16; // dips

    private static final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private int mCurItem;   // Index of currently displayed page.

    /**
     * 用于计算手滑动的速度
     */
    private VelocityTracker vt;

    private View mTopView;
    private View mBottomView;

    private Scroller mScroller;
    /**
     * 记录当前展示的是哪个view，0是topView，1是bottomView
     */
    private int mCurrentViewIndex = 0;
    /**
     * 手滑动距离，这个是控制布局的主要变量
     */
    private float mMoveLen;

    private boolean mIsBeingDragged;
    private boolean mIsUnableToDrag;
    private int mTouchSlop;
    private int mDefaultGutterSize;
    private int mGutterSize;

    private float mLastX;
    private float mLastY;

    /**
     * Position of the last motion event.
     */
    private float mLastMotionX;
    private float mLastMotionY;
    /**
     * Position of MotionEvent.ACTION_DOWN
     */
    private float mInitialMotionX;
    private float mInitialMotionY;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;
    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mFlingDistance;

    private List<OnPageChangeListener> mOnPageChangeListeners;

    public ScrollViewContainer(Context context) {
        this(context, null);
    }

    public ScrollViewContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScrollViewContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(true);
        final Context context = getContext();
        mScroller = new Scroller(context, sInterpolator);
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        final float density = context.getResources().getDisplayMetrics().density;
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
        mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
        mDefaultGutterSize = (int) (DEFAULT_GUTTER_SIZE * density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = MeasureSpec.getSize(heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h * 2, MeasureSpec.EXACTLY));
        mTopView.measure(widthMeasureSpec, heightMeasureSpec);
        mBottomView.measure(widthMeasureSpec, heightMeasureSpec);

        final int measuredWidth = getMeasuredWidth();
        final int maxGutterSize = measuredWidth / 10;
        mGutterSize = Math.min(maxGutterSize, mDefaultGutterSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int height = b - t;
        int w = r - l;
        mTopView.layout(0, 0, w, height / 2);
        mBottomView.layout(0, height / 2, w, height);
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() != 2) {
            throw new RuntimeException("DownUpScrollView can only have 2 direct view");
        }
        mTopView = getChildAt(0);
        mBottomView = getChildAt(1);
    }

    /**
     * 添加滚动回调接口. See {@link OnPageChangeListener}.
     *
     * @param listener listener to add
     */
    public void addOnPageChangeListener(OnPageChangeListener listener) {
        if (mOnPageChangeListeners == null) {
            mOnPageChangeListeners = new ArrayList<>();
        }
        mOnPageChangeListeners.add(listener);
    }

    /**
     * 清空回调
     */
    public void clearOnPageChangeListeners() {
        if (mOnPageChangeListeners != null) {
            mOnPageChangeListeners.clear();
        }
    }

    /**
     * 获取当前显示View
     *
     * @return
     */
    public int getCurrentShow() {
        return mCurrentViewIndex;
    }

    public int getScrollOffset() {
        return (int) (mTopView.getScrollY() + (-mMoveLen));
    }

    private onScrollChangeListener mScrollListener;

    public void setDispatchTouchListener(onScrollChangeListener listener) {
        this.mScrollListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
        // Always take care of the touch gesture being complete.
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            // Release the drag.
            mIsBeingDragged = false;
            mIsUnableToDrag = false;
            mActivePointerId = INVALID_POINTER;
            if (mVelocityTracker != null) {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
            return false;
        }

        // Nothing more to do here if we have decided whether or not we
        // are dragging.
        if (action != MotionEvent.ACTION_DOWN) {
            if (mIsBeingDragged) {
                return true;
            }
            if (mIsUnableToDrag) {
                if (DEBUG) Log.v(TAG, "Intercept returning false!");
                return false;
            }
        }

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                * Locally do absolute value. mLastMotionY is set to the y value
                * of the down event.
                */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float dy = y - mLastMotionY;
                final float yDiff = Math.abs(dy);
                final float xDiff = Math.abs(x - mInitialMotionX);

                Log.d(TAG, "move mIsBeingDragged=" + mIsBeingDragged + "   mIsUnableToDrag=" + mIsUnableToDrag + "  yDiff=" + yDiff);
                if (dy != 0 && !isGutterDrag(mLastMotionY, dy) && canScroll((int) dy, (int) x, (int) y)) {
                    // Nested view has scrollable area under this point. Let it be handled there.
                    mLastMotionX = x;
                    mLastMotionY = y;
                    mIsUnableToDrag = true;
                    return false;
                }
                if (yDiff > mTouchSlop && yDiff * 0.5f > xDiff) {
                    mIsBeingDragged = true;
                    requestParentDisallowInterceptTouchEvent(true);
                    mLastMotionY = dy > 0 ? mInitialMotionY + mTouchSlop :
                            mInitialMotionY - mTouchSlop;
                    mLastMotionX = x;
                } else if (xDiff > mTouchSlop) {
                    // The finger has moved enough in the vertical
                    // direction to be counted as a drag...  abort
                    // any attempt to drag horizontally, to work correctly
                    // with children that have scrolling containers.
                    mIsUnableToDrag = true;
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    performDrag(y);
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                mLastMotionX = mInitialMotionX = ev.getX();
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mIsUnableToDrag = false;

                mScroller.computeScrollOffset();
                if (!mScroller.isFinished()) {
                    // Let the user 'catch' the pager as it animates.
                    mScroller.abortAnimation();
                    mIsBeingDragged = true;
                    requestParentDisallowInterceptTouchEvent(true);
                } else {
                    mIsBeingDragged = false;
                }

                Log.v(TAG, "Down at " + mLastMotionX + "," + mLastMotionY
                        + " mIsBeingDragged=" + mIsBeingDragged
                        + "mIsUnableToDrag=" + mIsUnableToDrag);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mIsBeingDragged;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
//        boolean topViewCanScroll = canScroll(false,-1,0,0);
//        if (topViewCanScroll){
//            super.requestDisallowInterceptTouchEvent(disallowIntercept);
//        }
        Log.d(TAG, "requestDisallowInterceptTouchEvent disallowIntercept=" + disallowIntercept);
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    private void requestParentDisallowInterceptTouchEvent(boolean disallowIntercept) {
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (getChildCount() == 0) {
            // Nothing to present or scroll; nothing to touch.
            return false;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();

        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                mScroller.abortAnimation();
                // Remember where the motion event started
                mLastMotionX = mInitialMotionX = ev.getX();
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                break;
            }
            case MotionEvent.ACTION_MOVE:

                if (!mIsBeingDragged) {
                    final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, pointerIndex);
                    final float xDiff = Math.abs(x - mLastMotionX);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);
                    final float yDiff = Math.abs(y - mLastMotionY);
                    if (DEBUG)
                        Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
                    if (yDiff > mTouchSlop && yDiff > xDiff) {
                        if (DEBUG) Log.v(TAG, "Starting drag!");
                        mIsBeingDragged = true;
                        requestParentDisallowInterceptTouchEvent(true);
                        mLastMotionY = y - mInitialMotionY > 0 ? mInitialMotionY + mTouchSlop :
                                mInitialMotionY - mTouchSlop;
                        mLastMotionX = x;

                        // Disallow Parent Intercept, just in case
                        ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                // Not else! Note that mIsBeingDragged can be set above.
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    final int activePointerIndex = MotionEventCompat.findPointerIndex(
                            ev, mActivePointerId);
                    final float y = MotionEventCompat.getY(ev, activePointerIndex);
                    performDrag(y);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialVelocity = (int) VelocityTrackerCompat.getYVelocity(
                            velocityTracker, mActivePointerId);
                    final int scrollY = getScrollY();
                    final int activePointerIndex =
                            MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    final float y = MotionEventCompat.getY(ev, activePointerIndex);
                    final int totalDelta = (int) (y - mInitialMotionY);
                    int nextPage = determineTargetPage(mCurItem, scrollY, initialVelocity,
                            totalDelta);
                    scrollToItem(nextPage, true, initialVelocity);

                    mActivePointerId = INVALID_POINTER;
                    endDrag();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged) {
                    scrollToItem(mCurItem, true, 0);
                    mActivePointerId = INVALID_POINTER;
                    endDrag();
                }
                break;
            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int index = MotionEventCompat.getActionIndex(ev);
                final float y = MotionEventCompat.getY(ev, index);
                mLastMotionY = y;
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }
            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                mLastMotionY = MotionEventCompat.getY(ev,
                        MotionEventCompat.findPointerIndex(ev, mActivePointerId));
                break;
        }
        return true;
    }



    @Override
    public void computeScroll() {
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();

            if (oldX != x || oldY != y) {
                scrollTo(x, y);
                //Todo
//                if (!pageScrolled(x)) {
//                    mScroller.abortAnimation();
//                    scrollTo(0, y);
//                }
            }

            // Keep on drawing until the animation has finished.
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private void scrollToItem(int item, boolean smoothScroll, int velocity) {
        final boolean dispatchSelected = mCurItem != item;
        mCurItem = item;
        int destY;
        int height = getHeight() / 2;
        destY = mCurItem == 0 ? 0 : height;
        if (smoothScroll) {
            smoothScrollTo(destY, velocity);
            if (dispatchSelected) {
                dispatchOnPageSelected(item);
            }
        } else {
            if (dispatchSelected) {
                dispatchOnPageSelected(item);
            }
            scrollTo(0, destY);
        }
    }

    private void dispatchOnPageSelected(int position) {
        if (mOnPageChangeListeners != null) {
            for (int i = 0, z = mOnPageChangeListeners.size(); i < z; i++) {
                OnPageChangeListener listener = mOnPageChangeListeners.get(i);
                if (listener != null) {
                    listener.onPageSelected(position);
                }
            }
        }
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param index 滑动的目标，上还是下
     */
    void smoothScrollTo(int index) {
        smoothScrollTo(index, 0);
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param destY    the number of pixels to scroll by on the Y axis
     * @param velocity the velocity associated with a fling, if applicable. (0 otherwise)
     */
    void smoothScrollTo(int destY, int velocity) {
        if (getChildCount() == 0) {
            return;
        }
        int sy = getScrollY();
        int dy = destY-sy;
        if (dy == 0) {
            return;
        }

        final int height = getHeight();
        final int halfHeight = height / 2;
        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dy) / height);
        final float distance = halfHeight + halfHeight *
                distanceInfluenceForSnapDuration(distanceRatio);

        int duration;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float pageDelta = (float) Math.abs(dy) / (height);
            duration = (int) ((pageDelta + 1) * 100);
        }
        duration = Math.min(duration, MAX_SETTLE_DURATION);

        mScroller.startScroll(0, sy, 0, dy, duration);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    private int determineTargetPage(int currentPage, float pageOffset, int velocity, int deltaY) {
        int targetPage;
        if (Math.abs(deltaY) > mFlingDistance && Math.abs(velocity) > mMinimumVelocity) {
            targetPage = velocity > 0 ? 0 : 1;
        } else {
            int height = getHeight() / 2;
            if (currentPage == 1){
                pageOffset = height - pageOffset;
            }
            if (pageOffset >= 0.5f * height) {
                targetPage = currentPage == 1 ? 0 : 1;
            } else {
                targetPage = currentPage;
            }
        }
        return targetPage;
    }

    private void performDrag(float y) {
        final float deltaY = mLastMotionY - y;
        mLastMotionY = y;

        float oldScrollY = getScrollY();
        float scrollY = oldScrollY + deltaY;

        int height = getHeight() / 2;

        if (scrollY < 0) {
            scrollY = 0;
        } else if (scrollY > height) {
            scrollY = height;
        }
        //Log.d(TAG,"performDrag  scrollY="+scrollY);
        // Don't lose the rounded component
        mLastMotionX += scrollY - (int) scrollY;
        scrollTo(0, (int) scrollY);
        onScrollChange();
    }

    private void endDrag() {
        mIsBeingDragged = false;
        mIsUnableToDrag = false;

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionY = MotionEventCompat.getY(ev, newPointerIndex);
            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    private boolean canScroll(int dy, int x, int y) {
        if (mCurItem == 0) {
            return canScroll(mTopView, dy, x, y);
        } else {
            return canScroll(mBottomView, dy, x, y);
        }
    }

    private boolean isGutterDrag(float y, float dy) {
        return (y < mGutterSize && dy > 0) || (y > getHeight() / 2 - mGutterSize && dy < 0);
    }

    /**
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v      View to test for vertical scrollability
     * @param dy     Delta scrolled in pixels
     * @param x      X coordinate of the active touch point
     * @param y      Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, int dy, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();

            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, dy, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }
        return ViewCompat.canScrollVertically(v, -dy);
    }

    private void onScrollChange() {
        if (mScrollListener != null) {
            mScrollListener.onScrollChange(this);
        }
    }

    public interface onScrollChangeListener {
        void onScrollChange(ScrollViewContainer v);
    }

}