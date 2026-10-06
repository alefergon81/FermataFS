package me.aap.fermata.auto;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.SessionInfo;
import androidx.car.app.validation.HostValidator;

/**
 * "UNI-TV": Fermata's own UI rendered directly on the Android Auto navigation surface, through
 * a private virtual display. Unlike FS Mirror, it needs no screen capture, overlay, accessibility
 * or root access.
 */
public class CarDisplayService extends CarAppService {

	@NonNull
	@Override
	public HostValidator createHostValidator() {
		return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
	}

	@NonNull
	@Override
	public Session onCreateSession(@NonNull SessionInfo sessionInfo) {
		return new CarDisplaySession(this);
	}
}
