package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.text.TextUtils.isBlank;

import android.content.Context;

import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

import me.aap.fermata.addon.tv.R;
import me.aap.utils.app.App;

/**
 * Turns the failures to download or import a playlist into messages for the user.
 */
public final class TvM3uErrors {

	private TvM3uErrors() {
	}

	/**
	 * The reason of the failure. The common network errors, whose exceptions have no message, are
	 * described. Otherwise it is the innermost message in the cause chain.
	 */
	public static String describe(Throwable err) {
		Context ctx = dynCtx(App.get());
		String msg = null;
		Throwable last = err;

		for (Throwable t = err; t != null; t = (t.getCause() == t) ? null : t.getCause()) {
			if ((t instanceof UnresolvedAddressException) || (t instanceof UnknownHostException)) {
				return ctx.getString(R.string.tv_err_host_not_found);
			}
			if ((t instanceof SSLException) || (t instanceof CertificateException)) {
				return ctx.getString(R.string.tv_err_ssl, message(t));
			}

			String m = t.getLocalizedMessage();
			if ((m != null) && !isBlank(m)) msg = m;
			last = t;
		}

		return (msg != null) ? msg : last.getClass().getSimpleName();
	}

	private static String message(Throwable t) {
		String m = t.getLocalizedMessage();
		return ((m != null) && !isBlank(m)) ? m : t.getClass().getSimpleName();
	}
}
