package me.aap.fermata.auto;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.OnBackPressedDispatcherOwner;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.ActivityResultRegistryOwner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentHostCallback;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryOwner;

/**
 * Hosts fragments for {@link CarHostActivity}. {@code FragmentManager.attachController} looks for
 * the owners on this callback, so it implements them by delegating to the host, like
 * {@code FragmentActivity.HostCallbacks}.
 */
final class CarFragmentHost extends FragmentHostCallback<CarHostActivity>
		implements LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner,
		OnBackPressedDispatcherOwner, ActivityResultRegistryOwner {
	private final CarHostActivity host;

	CarFragmentHost(CarHostActivity host) {
		super(host, new Handler(Looper.getMainLooper()), 0);
		this.host = host;
	}

	@Override
	public CarHostActivity onGetHost() {
		return host;
	}

	@NonNull
	@Override
	public LayoutInflater onGetLayoutInflater() {
		// A fresh clone per fragment: Fragment.getLayoutInflater() sets a factory on it.
		return host.getLayoutInflater().cloneInContext(host);
	}

	@Override
	public boolean onHasWindowAnimations() {
		return false;
	}

	@Nullable
	@Override
	public View onFindViewById(int id) {
		return host.findViewById(id);
	}

	@Override
	public boolean onHasView() {
		return host.getContentRoot() != null;
	}

	@NonNull
	@Override
	public Lifecycle getLifecycle() {
		return host.getLifecycle();
	}

	@NonNull
	@Override
	public ViewModelStore getViewModelStore() {
		return host.getViewModelStore();
	}

	@NonNull
	@Override
	public SavedStateRegistry getSavedStateRegistry() {
		return host.getSavedStateRegistry();
	}

	@NonNull
	@Override
	public OnBackPressedDispatcher getOnBackPressedDispatcher() {
		return host.getOnBackPressedDispatcher();
	}

	@NonNull
	@Override
	public ActivityResultRegistry getActivityResultRegistry() {
		return host.getActivityResultRegistry();
	}
}
