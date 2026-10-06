package me.aap.fermata.auto;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.log.Log;

/**
 * A private {@link VirtualDisplay} on the car surface with a {@link Presentation} on it. A display
 * owned by the app needs no permission. Surface changes only swap or resize the display, so the
 * Presentation, and everything shown in it, survives them.
 */
final class CarDisplay {
	private final Context ctx;
	private VirtualDisplay vd;
	private CarPresentation presentation;
	private int width;
	private int height;

	CarDisplay(Context ctx) {
		this.ctx = ctx;
	}

	@Nullable
	Presentation getPresentation() {
		return presentation;
	}

	int getWidth() {
		return width;
	}

	int getHeight() {
		return height;
	}

	/**
	 * Attaches the surface, creating the display and Presentation on the first call.
	 *
	 * @return true if the Presentation has just been created
	 */
	boolean setSurface(@NonNull Surface surface, int width, int height, int dpi) {
		this.width = width;
		this.height = height;

		if (vd == null) {
			DisplayManager dm = ctx.getSystemService(DisplayManager.class);
			vd = dm.createVirtualDisplay("FermataCar", width, height, dpi, surface, 0);
			Log.i("Car display created: ", width, 'x', height, " dpi=", dpi);
			presentation = new CarPresentation(ctx, vd.getDisplay());
			presentation.show();
			return true;
		}

		vd.setSurface(surface);
		resize(width, height, dpi);
		return false;
	}

	void resize(int width, int height, int dpi) {
		if (vd == null) return;
		this.width = width;
		this.height = height;
		Display d = vd.getDisplay();
		Log.i("Car display resized: ", width, 'x', height, " dpi=", dpi, " display=", d.getDisplayId());
		vd.resize(width, height, dpi);
	}

	void detachSurface() {
		if (vd != null) vd.setSurface(null);
	}

	void release() {
		if (presentation != null) {
			presentation.release();
			presentation = null;
		}
		if (vd != null) {
			vd.release();
			vd = null;
		}
		Log.i("Car display released");
	}

	private static final class CarPresentation extends Presentation {
		private boolean released;

		CarPresentation(Context outerContext, Display display) {
			super(outerContext, display);
		}

		// Presentation cancels itself when the display metrics change. A resize must keep the UI
		// (and playback) alive, so only an explicit release dismisses it.
		@Override
		public void cancel() {
			if (released) super.cancel();
			else Log.i("Ignoring Presentation cancel on display change");
		}

		void release() {
			released = true;
			dismiss();
		}
	}
}
