package com.rgds.ultimate.shell;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Shared input policy used by both display Activities.
 *
 * Face buttons and DPAD are consumed before Android can apply fallback mappings. F10 (RG),
 * volume keys, L2/R2 and the physical HOME/BACK scan code are intentionally left to Android.
 */
final class ShellInputRouter {
    private static final float STICK_DEAD_ZONE = 0.55f;
    private static final long ANALOG_REPEAT_MS = 220L;
    private static final long COMPATIBILITY_DEDUP_MS = 150L;

    private final ShellStateRepository repository;
    private final ShellActionHandler actions;
    // Both Activity routers share dedup history: RG may route the paired event to the other display.
    private static int lastAnalogDirection;
    private static int lastAnalogTriggerDirection;
    private static int lastAnalogDeviceId = -1;
    private static long lastAnalogTriggerAt;
    private static long lastAnalogEventAt;
    private static int lastHatDirection;
    private static int lastKeyDirection;
    private static int lastKeyDeviceId = -1;
    private static long lastKeyTriggerAt;

    ShellInputRouter(ShellStateRepository repository, ShellActionHandler actions) {
        this.repository = repository;
        this.actions = actions;
    }

    boolean dispatchKeyEvent(KeyEvent event) {
        long callbackStarted=android.os.SystemClock.uptimeMillis();
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            lastAnalogDirection=0;
            lastHatDirection=0;
            repository.flush();
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_F10
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }

        if (!isConsumedGameKey(keyCode)) {
            return false;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN)
            { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGrid","ROUTE_KEY code="+keyCode+" scan="+event.getScanCode()+
                    " source="+event.getSource()+" focus="+repository.focusDisplay()); }

        if(ShellCoordinator.get().launchPending()&&!ShellDialogBuilder.hasOpenDialog()&&keyCode==KeyEvent.KEYCODE_BUTTON_B){
            String key=event.getDeviceId()+":"+keyCode,context="CANCEL_LAUNCH|"+ShellCoordinator.get().launchEpoch()+"|"+actionContext();
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)ActionKeyLatch.press(key,context);
            else if(event.getAction()==KeyEvent.ACTION_UP&&ActionKeyLatch.release(key,context,event.isCanceled()))actions.onBackRequested();
            return true;
        }
        if (!ShellCoordinator.get().canInteract() || ShellDialogBuilder.hasOpenDialog()
                || repository.snapshot().searchEditing || repository.snapshot().searchComposing) {
            ActionKeyLatch.reset();
            return true;
        }

        int direction = directionForKey(keyCode);
        if (direction != 0) {
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
            long eventTime = event.getEventTime();
            boolean analogDuplicate = lastHatDirection==0 && event.getDeviceId() == lastAnalogDeviceId
                    && direction == lastAnalogTriggerDirection
                    && ((direction == lastAnalogDirection && eventTime-lastAnalogEventAt<250L) ||
                        Math.abs(eventTime-lastAnalogTriggerAt)<=COMPATIBILITY_DEDUP_MS);
            boolean tooFastRepeat=event.getRepeatCount()>0 && direction==lastKeyDirection &&
                    event.getDeviceId()==lastKeyDeviceId && eventTime-lastKeyTriggerAt<130L;
            if (!analogDuplicate && !tooFastRepeat) {
                lastKeyDirection=direction; lastKeyDeviceId=event.getDeviceId(); lastKeyTriggerAt=eventTime;
                long callback=callbackStarted;
                repository.beginInput(eventTime,callback);
                move(direction);
                long done=android.os.SystemClock.uptimeMillis();
                { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGridPerf","INPUT event="+eventTime+" callback="+callback+" state="+repository.snapshot().stateTime+" returned="+done+
                    " sequence="+repository.snapshot().inputSequence+" display="+repository.focusDisplay()+
                    " device="+event.getDeviceId()+" source="+event.getSource()+" repeat="+event.getRepeatCount()); }
                repository.endInput();
                { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGrid","NAV_KEY code="+keyCode+" scan="+event.getScanCode()+
                    " device="+event.getDeviceId()+" time="+eventTime+" source="+event.getSource()); }
            } else if(analogDuplicate) {
                { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGrid","DEDUP_KEY direction="+direction+" time="+eventTime); }
            }
            return true;
        }

        String key=event.getDeviceId()+":"+keyCode;
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getRepeatCount()==0)ActionKeyLatch.press(key,actionContext());
            return true;
        }
        if(event.getAction()!=KeyEvent.ACTION_UP||!ActionKeyLatch.release(key,actionContext(),event.isCanceled()))return true;
        { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGrid","BUTTON code="+keyCode+" scan="+event.getScanCode()+
                " focus="+repository.focusDisplay()); }
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                actions.onLaunchRequested();
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                actions.onBackRequested();
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                repository.toggleFavorite();
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                actions.onDetailsRequested();
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                repository.switchCategory(-1);
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                repository.switchCategory(1);
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                actions.onLocalSearchRequested();break;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                actions.onFilterRequested();
                break;
            default:
                break;
        }
        return true;
    }

    boolean dispatchGenericMotionEvent(MotionEvent event) {
        if(repository.snapshot().searchEditing||repository.snapshot().searchComposing||ShellDialogBuilder.hasOpenDialog())return true;
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                || event.getAction() != MotionEvent.ACTION_MOVE) {
            return false;
        }

        float x = centeredAxis(event, MotionEvent.AXIS_X);
        float y = centeredAxis(event, MotionEvent.AXIS_Y);
        lastHatDirection=dominantDirection(centeredAxis(event,MotionEvent.AXIS_HAT_X),
                centeredAxis(event,MotionEvent.AXIS_HAT_Y));
        int direction = dominantDirection(x, y);
        lastAnalogEventAt=event.getEventTime();
        if(!ShellCoordinator.get().canInteract()) { lastAnalogDirection=0; return true; }
        if (direction == 0) {
            lastAnalogDirection = 0;
            // V0.1: this D-pad is HAT motion -> Android synthetic DPAD keys.
            // Let neutral/HAT motion (including release) reach that synthesizer.
            if(lastHatDirection!=0){ if(PerfTrace.isEnabled()) android.util.Log.i("TwinGrid","HAT_TO_DPAD direction="+lastHatDirection+
                    " device="+event.getDeviceId()+" time="+event.getEventTime()); }
            return false;
        }

        long eventTime = event.getEventTime();
        boolean keyDuplicate = event.getDeviceId() == lastKeyDeviceId
                && direction == lastKeyDirection
                && Math.abs(eventTime - lastKeyTriggerAt) <= COMPATIBILITY_DEDUP_MS;
        boolean shouldTrigger = direction != lastAnalogDirection
                || event.getDeviceId() != lastAnalogDeviceId
                || eventTime - lastAnalogTriggerAt >= ANALOG_REPEAT_MS;

        lastAnalogDirection = direction;
        lastAnalogDeviceId = event.getDeviceId();
        if (shouldTrigger) {
            lastAnalogTriggerAt = eventTime;
            lastAnalogTriggerDirection=direction;
            if (!keyDuplicate) {
                long callback=android.os.SystemClock.uptimeMillis();
                repository.beginInput(eventTime,callback);
                move(direction);
                { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGridPerf","INPUT_AXIS event="+eventTime+" callback="+callback+
                    " state="+android.os.SystemClock.uptimeMillis()+" sequence="+repository.snapshot().inputSequence+
                    " display="+repository.focusDisplay()+" device="+event.getDeviceId()); }
                repository.endInput();
                { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGrid","NAV_AXIS direction="+direction+" x="+x+" y="+y+
                    " time="+eventTime+" device="+event.getDeviceId()); }
            }
        }
        return true;
    }

    private static boolean isConsumedGameKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return true;
            default:
                return false;
        }
    }

    private static int directionForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return 1;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return 2;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return 3;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return 4;
            default:
                return 0;
        }
    }

    private String actionContext(){ShellStateRepository.Snapshot s=repository.snapshot();if(s.page==ShellStateRepository.Page.PREPARING)return "PREPARING|"+repository.setupControlKey()+"|"+s.currentFocusDisplay;return s.actionContext()+(s.page==ShellStateRepository.Page.TASKS||s.page==ShellStateRepository.Page.PREPARING?"|"+s.taskIndex+"|"+repository.taskControlKey():"");}
    private void move(int direction) {
        repository.navigate(direction);
    }

    private static float centeredAxis(MotionEvent event, int axis) {
        float value = event.getAxisValue(axis);
        return Math.abs(value) < STICK_DEAD_ZONE ? 0f : value;
    }

    private static int dominantDirection(float x, float y) {
        if (x == 0f && y == 0f) {
            return 0;
        }
        if (Math.abs(x) > Math.abs(y)) {
            return x < 0f ? 3 : 4;
        }
        return y < 0f ? 1 : 2;
    }
}
