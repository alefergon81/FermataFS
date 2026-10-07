package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertEquals;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_AA;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_CLASSIC;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_MAIN;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_PURPLE;

import org.junit.Test;

public class CarThemeDefaultTest {

	@Test
	public void carThemeDefaultsToPurple() {
		assertEquals(THEME_PURPLE, THEME_AA.getDefaultValue().getAsInt());
	}

	@Test
	public void phoneThemeDefaultUnchanged() {
		assertEquals(THEME_CLASSIC, THEME_MAIN.getDefaultValue().getAsInt());
	}
}
