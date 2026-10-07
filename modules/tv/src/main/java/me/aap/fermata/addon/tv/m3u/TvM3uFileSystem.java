package me.aap.fermata.addon.tv.m3u;

import androidx.annotation.NonNull;

import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.fermata.vfs.m3u.M3uFileSystem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.resource.Rid;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uFileSystem extends M3uFileSystem {
	public static final String SCHEME_TVM3U = "tvm3u";
	private static final TvM3uFileSystem instance = new TvM3uFileSystem();

	public static TvM3uFileSystem getInstance() {
		return instance;
	}

	@Override
	public String getScheme() {
		return SCHEME_TVM3U;
	}

	@NonNull
	@Override
	public FutureSupplier<TvM3uFile> getResource(Rid rid) {
		return super.getResource(rid).cast();
	}

	/**
	 * Downloads the playlist again if its max age is reached or its stamps were cleared.
	 */
	public FutureSupplier<TvM3uFile> reload(TvM3uFile file) {
		return load(file).cast();
	}

	@Override
	protected HttpFileDownloader.StatusListener createProgressListener(M3uFile file) {
		return TvM3uImport.get(((TvM3uFile) file).getPlaylistDbFile()).createDownloadListener();
	}

	protected TvM3uFile createM3uFile(Rid rid) {
		return new TvM3uFile(rid);
	}
}
