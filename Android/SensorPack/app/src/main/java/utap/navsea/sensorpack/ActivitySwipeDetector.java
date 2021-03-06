package utap.navsea.sensorpack;

import android.app.Activity;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;

/**
 * The below code is originally from:
 * http://stackoverflow.com/questions/937313/fling-gesture-detection-on-grid-layout
 * It has been modified to switch between the activities in this app
 */
public class ActivitySwipeDetector implements View.OnTouchListener {
    private Activity activity;
    static final int MIN_DISTANCE = 100;
    private float downX, downY;
    private Class leftActivity;
    private Class rightActivity;

    public ActivitySwipeDetector(Activity activity){
        this.activity = activity;
    }

    public void setDestinations(Class left, Class right){
        leftActivity = left;
        rightActivity = right;
    }

    public void onRightSwipe(){
        Intent intentApp = new Intent(activity, rightActivity);
        activity.startActivity(intentApp);
    }

    public void onLeftSwipe(){
        Intent intentApp = new Intent(activity, leftActivity);
        activity.startActivity(intentApp);
    }

    public void onDownSwipe(){
    }

    public void onUpSwipe(){
    }

    public boolean onTouch(View v, MotionEvent event) {
        boolean streaming_rt = false; //Check the state of the button on whatever activity we're currently on
        if(leftActivity==TempCondActivity.class) streaming_rt = DepthLightActivity.isStreamingRT();
        if(leftActivity==MainActivity.class) streaming_rt = TempCondActivity.isStreamingRT();
        if(leftActivity==DepthLightActivity.class) streaming_rt = MainActivity.isStreamingRT();

        if(!streaming_rt) { //Make sure we aren't downloading data when we try to switch activities
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    downX = event.getX();
                    downY = event.getY();
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float upX = event.getX();
                    float upY = event.getY();

                    float deltaX = downX - upX;
                    float deltaY = downY - upY;

                    // swipe horizontal?
                    if (Math.abs(deltaX) > Math.abs(deltaY)) {
                        if (Math.abs(deltaX) > MIN_DISTANCE) {
                            // left or right
                            if (deltaX > 0) {
                                this.onRightSwipe();
                                return true;
                            }
                            if (deltaX < 0) {
                                this.onLeftSwipe();
                                return true;
                            }
                        } else {
                            return false; // We don't consume the event
                        }
                    }
                    // swipe vertical?
                    else {
                        if (Math.abs(deltaY) > MIN_DISTANCE) {
                            // top or down
                            if (deltaY < 0) {
                                this.onDownSwipe();
                                return true;
                            }
                            if (deltaY > 0) {
                                this.onUpSwipe();
                                return true;
                            }
                        } else {
                            return false; // We don't consume the event
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

}