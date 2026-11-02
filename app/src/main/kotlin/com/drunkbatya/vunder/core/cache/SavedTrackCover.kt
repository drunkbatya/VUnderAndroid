package com.drunkbatya.vunder.core.cache

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.drunkbatya.vunder.core.model.Track
import okio.Buffer

data class SavedTrackCover(val track: Track, val isLarge: Boolean)

class SavedTrackCoverFetcher(
    private val cover: SavedTrackCover,
    private val options: Options,
    private val downloads: DownloadCenter,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val image = downloads.saveCover(cover.track, cover.isLarge)
        return SourceFetchResult(ImageSource(Buffer().write(image), options.fileSystem), null, DataSource.NETWORK)
    }

    class Factory(private val downloads: DownloadCenter) : Fetcher.Factory<SavedTrackCover> {
        override fun create(data: SavedTrackCover, options: Options, imageLoader: ImageLoader): Fetcher =
            SavedTrackCoverFetcher(data, options, downloads)
    }
}

class SavedTrackCoverKeyer : Keyer<SavedTrackCover> {
    override fun key(data: SavedTrackCover, options: Options): String = "saved-cover:${data.track.storageId}:${data.isLarge}"
}
