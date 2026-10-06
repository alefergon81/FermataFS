package me.aap.fermata.ui.fragment;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.play.core.splitcompat.SplitCompat;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
public abstract class MainActivityFragment extends ActivityFragment {

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		SplitCompat.install(context);
	}

	@Override
	public MainActivityDelegate getActivityDelegate() {
		return (MainActivityDelegate) super.getActivityDelegate();
	}

	@Override
	public NavBarMediator getNavBarMediator() {
		return getActivityDelegate().getNavBarMediator();
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FloatingButtonMediator.instance;
	}

	@CallSuper
	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) discardSelection();
	}

	@Override
	public boolean onBackPressed() {
		discardSelection();
		return super.onBackPressed();
	}

	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
	}

	public void discardSelection() {
	}

	public boolean isVideoModeSupported() {
		return false;
	}

	/**
	 * Toggles the full-screen video of this screen, if it has one.
	 *
	 * @return false if not supported
	 */
	public boolean toggleFullScreen() {
		return false;
	}

	/**
	 * Lets the car UI start a new drag somewhere other than its origin, for example on the list this
	 * screen wants to scroll. Coordinates are car display (surface) pixels.
	 *
	 * @param originTime {@code SystemClock.uptimeMillis()} of the tap or pinch that set the origin,
	 *                   or -1 if there was none
	 * @return {x, y} where the drag starts instead, or null to start at the origin
	 */
	@Nullable
	public float[] getCarDragOrigin(float x, float y, long originTime) {
		return null;
	}

	/**
	 * Returns this screen to its own start (for example a web page's home URL).
	 *
	 * @return false if not supported
	 */
	public boolean goHome() {
		return false;
	}

	public boolean isVoiceCommandsSupported() {
		return false;
	}

	public boolean startVoiceAssistant() {
		return false;
	}

	public void voiceCommand(VoiceCommand cmd) {
	}
}
