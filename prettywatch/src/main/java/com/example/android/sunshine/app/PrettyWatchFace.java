package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.util.Calendar;
import java.lang.ref.WeakReference;
/**
 * Created by yusuf on 15/12/16.
 */


//Reference https://www.codeproject.com/articles/1031696/creating-the-watch-faces-for-android-wear?msg=5292294

public class PrettyWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "PrettyWatchFace";

    private static final int MSG_UPDATE_TIME = 0;

    private GoogleApiClient mGoogleApiClient;
    private String mMaxTemp;
    private String mMinTemp;
    private int mDayWeather;

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

   /* @Override
    public Engine onCreateEngine() {
        return new Engine();
    } */

    @Override
    public SimpleEngine onCreateEngine() {
        return new SimpleEngine();
    }

    private static class EngineHandler extends Handler {

        private final WeakReference<PrettyWatchFace.SimpleEngine> mWeakReference;

        public EngineHandler(PrettyWatchFace.SimpleEngine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            PrettyWatchFace.SimpleEngine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }


    }

    private class SimpleEngine  extends CanvasWatchFaceService.Engine
                                implements  GoogleApiClient.ConnectionCallbacks,
                                            GoogleApiClient.OnConnectionFailedListener,
                                            DataApi.DataListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        Boolean mIsTimeZoneRecieverRegistered  = false;
        Boolean mIsLowBitAmbient = false;
        Boolean mIsInAmbientMode =false;


        Paint mBackgroundPaint;
        Paint mTextPaint;

        float mXOffset;
        float mYOffset;

        Paint mDatePaint;
        float mDateXOffset;
        float mDateYOffset;

        Paint mMaxTempPaint;
        Paint mMinTempPaint;
        float mYOffsetWeather;


        Calendar mCalendar;
        
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };


        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(PrettyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());



            createPaintAndOffsetValues();

            mCalendar = Calendar.getInstance();

            mGoogleApiClient = new GoogleApiClient.Builder(PrettyWatchFace.this)
                                    .addConnectionCallbacks(this)
                                    .addOnConnectionFailedListener(this)
                                    .addApi(Wearable.API)
                                    .build();


        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            super.onDestroy();
        }

        private void createPaintAndOffsetValues() {
            Resources resources = PrettyWatchFace.this.getResources();

            mYOffset = resources.getDimension(R.dimen.y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.wear_background));

            mTextPaint = new Paint();
            mTextPaint = createPaint(resources.getColor(R.color.wear_time_font_color),
                    resources.getDimension(R.dimen.time_font_size));
            mXOffset = mTextPaint.measureText("00:00") / 2;

            mDatePaint = new Paint();
            mDatePaint = createPaint(resources.getColor(R.color.wear_date_font_color),
                    resources.getDimension(R.dimen.date_font_size));
            mDateXOffset = mDatePaint.measureText("XXX, XXX 10 2016") / 2;
            mDateYOffset = resources.getDimension(R.dimen.y_offset_date);

            mMaxTempPaint = new Paint();
            mMaxTempPaint = createPaint(resources.getColor(R.color.wear_temp_font_color),
                    resources.getDimension(R.dimen.temp_font_size));
            mMinTempPaint = new Paint();
            mMinTempPaint = createPaint(resources.getColor(R.color.wear_temp_font_color),
                    resources.getDimension(R.dimen.temp_font_size));
            mYOffsetWeather = resources.getDimension(R.dimen.y_offset_weather);


        }

        private Paint createPaint(int textColor, float textSize) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setAntiAlias(true);
            paint.setTextSize(textSize);
            return paint;
        }



        @Override
        public void onVisibilityChanged(boolean visible) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onVisibilityChanged: " + visible);
            }
            super.onVisibilityChanged(visible);

            if(visible) {
                mGoogleApiClient.connect();
                registerTimeZoneReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
            else {
                if (mGoogleApiClient !=null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
                unRegisterTimeZoneReceiver();
            }

            updateTimer();
        }

        private void registerTimeZoneReceiver() {
            if (mIsTimeZoneRecieverRegistered) {
                return;
            }
            mIsTimeZoneRecieverRegistered = true;
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
            PrettyWatchFace.this.registerReceiver(mTimeZoneReceiver, intentFilter);
        }

        private void unRegisterTimeZoneReceiver() {
            if (!mIsTimeZoneRecieverRegistered) {
                return;
            }
            mIsTimeZoneRecieverRegistered = false;
            PrettyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mIsLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: burn-in protection = ");
            }
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }

            if(mIsInAmbientMode != inAmbientMode) {
                mIsInAmbientMode = inAmbientMode;
                if(mIsLowBitAmbient){
                    mTextPaint.setAntiAlias(!mIsInAmbientMode);
                }
                invalidate();
            }

            updateTimer();
        }

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    break;
            }
            invalidate();
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if(isVisible() && !isInAmbientMode()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }



        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            //First Draw the Background
            if(isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            }
            else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint );
            }

            //Next Draw the time HH:MM (when in ambient) else HH:MM:SS in interactive mode
            mCalendar.setTimeInMillis(System.currentTimeMillis());

            String timeString = String.format("%02d:%02d",
                    mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE));

            canvas.drawText(timeString, bounds.centerX() - mXOffset, mYOffset, mTextPaint);

            //Draw Date
            String dayString = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK,
                    Calendar.SHORT, Locale.getDefault());
            String monthString = mCalendar.getDisplayName(Calendar.MONTH,
                    Calendar.SHORT, Locale.getDefault());
            int dateNo = mCalendar.get(Calendar.DAY_OF_MONTH);
            int yearNo = mCalendar.get(Calendar.YEAR);

            String dateString = String.format("%s, %s %d %d",
                    dayString.toUpperCase(), monthString.toUpperCase(), dateNo, yearNo);

            canvas.drawText(dateString, bounds.centerX() - mDateXOffset, mDateYOffset, mDatePaint);

            //Draw Weather data received from App

            if(mMaxTemp != null && mMinTemp != null) {

                float maxTempTextSize = mMaxTempPaint.measureText(mMaxTemp);
                float minTempTextSize = mMinTempPaint.measureText(mMinTemp);

                if(mIsInAmbientMode) {
                    mMinTempPaint.setColor(getResources().getColor(R.color.wear_temp_font_color));
                    float xAmbientModeOffset = bounds.centerX() - ((maxTempTextSize + minTempTextSize + 20) / 2);
                    canvas.drawText(mMaxTemp, xAmbientModeOffset, mYOffsetWeather, mMaxTempPaint);
                    canvas.drawText(mMinTemp, xAmbientModeOffset + maxTempTextSize + 20, mYOffsetWeather, mMinTempPaint);
                } else {

                    mMinTempPaint.setColor(getResources().getColor(R.color.wear_temp_font_color));
                    float xOffset = bounds.centerX() - (maxTempTextSize  / 2);
                    Drawable weatherIconBitMap = getResources().getDrawable(getIconResourceForWeatherCondition(mDayWeather));
                 //   Drawable weatherIconBitMap = getResources().getDrawable(R.drawable.wear_clear);
                    Bitmap iconBitmap = ((BitmapDrawable)weatherIconBitMap).getBitmap();
                    float scaledWidth = (mMaxTempPaint.getTextSize() / iconBitmap.getHeight())*iconBitmap.getWidth();
                    Bitmap weatherIcon = Bitmap.createScaledBitmap(iconBitmap,
                            (int) scaledWidth, (int) mMaxTempPaint.getTextSize(), true);


                    canvas.drawText(mMaxTemp, bounds.centerX()
                            - ((weatherIcon.getWidth() /2) + maxTempTextSize +25), mYOffsetWeather, mMaxTempPaint);
                    canvas.drawText(mMinTemp, bounds.centerX()
                            +  ((weatherIcon.getWidth() /2)  + 25), mYOffsetWeather, mMinTempPaint);
                    canvas.drawBitmap(weatherIcon, xOffset, mYOffsetWeather - weatherIcon.getHeight(), null);

                }
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }
            invalidate();
        }

        private void handleUpdateTimeMessage() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "handleupdateTimer");
            }
            invalidate();
            if(isVisible() && !isInAmbientMode()) {
                long timeInMilliSeconds = System.currentTimeMillis();
                long delayInMilliSeconds = INTERACTIVE_UPDATE_RATE_MS - (timeInMilliSeconds % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayInMilliSeconds);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {



            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {

                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();

                    String path = dataEvent.getDataItem().getUri().getPath();
                    if (path.equals("/wearable-weather-data")) {
                        mMaxTemp = dataMap.getString("max-temp");
                        mMinTemp = dataMap.getString("min-temp");
                        mDayWeather = dataMap.getInt("day-weather");
                        String logString = "MaxTemp :" + mMaxTemp + ": MinTemp :" + mMinTemp + ": DayWeather:" + mDayWeather + ":";
                        Log.v("Wear incoming data", logString );
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Config DataItem updated:");
                        }
                        invalidate();
                    }
                }
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

        /**
         * Helper method to provide the icon resource id according to the weather condition id returned
         * by the OpenWeatherMap call.
         * @param weatherId from OpenWeatherMap API response
         * @return resource id for the corresponding icon. -1 if no relation is found.
         */
        public  int getIconResourceForWeatherCondition(int weatherId) {
            // Based on weather code data found at:
            // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.wear_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.wear_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.wear_rain;
            } else if (weatherId == 511) {
                return R.drawable.wear_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.wear_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.wear_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.wear_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.wear_storm;
            } else if (weatherId == 800) {
                return R.drawable.wear_clear;
            } else if (weatherId == 801) {
                return R.drawable.wear_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.wear_clouds;
            }
            return -1;
        }


    }


}
