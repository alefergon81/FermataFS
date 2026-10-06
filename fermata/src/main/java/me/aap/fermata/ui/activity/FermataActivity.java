package me.aap.fermata.ui.activity;

import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import me.aap.utils.ui.activity.AppActivity;

/**
 * @author Andrey Pavlenko
 */
public interface FermataActivity extends AppActivity {


	boolean isCarActivity();

	void setRequestedOrientation(int requestedOrientation);

	/**
	 * The fragment to show on start, or {@code ID_NULL} to use the default start logic.
	 */
	@IdRes
	default int getStartFragmentId() {
		return ID_NULL;
	}

	@Nullable
	default EditText startInput(TextWatcher w) {
		return null;
	}

	default void stopInput() {
	}

	default boolean isInputActive() {
		return false;
	}

	default boolean setTextInput(String text) {
		return false;
	}
}
