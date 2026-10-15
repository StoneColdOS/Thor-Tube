package io.github.stonecoldos.thortube

import android.app.Application
import io.github.stonecoldos.thortube.source.ExtractorDownloader
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class ThorTubeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        io.github.stonecoldos.thortube.youtube.YouTubeAccount.init(this)
        http = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // Ask YouTube for results in the Thor's language and country. Left unset, the extractor
        // pretends to be in Great Britain, and searches pull in more foreign-language videos.
        val locale = Locale.getDefault()
        val country = locale.country.ifBlank { "US" }
        NewPipe.init(ExtractorDownloader(http), Localization.fromLocale(locale), ContentCountry(country))
        AppLog.i("ThorTube", "YouTube language ${locale.language}, country $country")
        // The iOS client hands back an HLS manifest, which we keep as a fallback stream.
        YoutubeStreamExtractor.setFetchIosClient(true)
    }

    companion object {
        /** One shared HTTP client for the extractor and the player. */
        lateinit var http: OkHttpClient
            private set
    }
}
