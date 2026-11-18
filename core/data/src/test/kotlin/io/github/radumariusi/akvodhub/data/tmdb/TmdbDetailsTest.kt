package io.github.radumariusi.akvodhub.data.tmdb

import io.github.radumariusi.akvodhub.parser.match.MediaType
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TmdbDetailsTest {
    @Test
    fun romanianDescriptionUsesOneLocalizedRequestWithoutExtraDetails() =
        runBlocking {
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        assertEquals("ro-RO", chain.request().url.queryParameter("language"))
                        assertNull(chain.request().url.queryParameter("append_to_response"))
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"id":1,"overview":"Descriere română"}""".toResponseBody())
                            .build()
                    }.build()
            assertEquals("Descriere română", TmdbClient(http).romanianOverview("example", MediaType.MOVIE, 1))
        }

    @Test
    fun forbiddenDetailsAreAnErrorRatherThanAMissingTitle() =
        runBlocking {
            var calls = 0
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        calls++
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(403)
                            .message("Forbidden")
                            .body("{}".toResponseBody())
                            .build()
                    }.build()
            val error = runCatching { TmdbClient(http).details("example", MediaType.MOVIE, 1) }.exceptionOrNull()
            assertTrue(error is IOException)
            assertEquals(1, calls)
        }

    @Test
    fun onlyNotFoundReturnsMissingDetails() =
        runBlocking {
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(404)
                            .message("Not Found")
                            .body("{}".toResponseBody())
                            .build()
                    }.build()
            assertNull(TmdbClient(http).details("example", MediaType.TV, 1))
        }

    @Test
    fun movieAndTvAppendTheRequiredMetadataInOneRequestEach() =
        runBlocking {
            val appended = mutableListOf<String?>()
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        appended += chain.request().url.queryParameter("append_to_response")
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"id":1}""".toResponseBody())
                            .build()
                    }.build()
            val client = TmdbClient(http)
            client.details("example", MediaType.MOVIE, 1)
            client.details("example", MediaType.TV, 1)
            assertEquals(listOf("external_ids,release_dates", "external_ids,content_ratings"), appended)
        }

    @Test
    fun movieAndTvLogoRequestsIncludeEnglishAndUnlabelledImages() =
        runBlocking {
            val requests = mutableListOf<HttpUrl>()
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        requests += chain.request().url
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                """{"logos":[{"file_path":"/title.svg","iso_639_1":"en"},{"file_path":"/neutral.png"}]}"""
                                    .toResponseBody(),
                            ).build()
                    }.build()
            val client = TmdbClient(http)
            val expected = listOf(TmdbLogo("/title.svg", "en"), TmdbLogo("/neutral.png"))
            assertEquals(expected, client.images("example", MediaType.MOVIE, 1).logos)
            assertEquals(expected, client.images("example", MediaType.TV, 2).logos)
            assertEquals(listOf("/3/movie/1/images", "/3/tv/2/images"), requests.map { it.encodedPath })
            assertTrue(requests.all { it.queryParameter("language") == "en-US" })
            assertTrue(requests.all { it.queryParameter("include_image_language") == "en,null" })
        }

    @Test
    fun focusedEpisodeRequestsEnglishNameSummaryStillAndDuration() =
        runBlocking {
            var episodeUrl: HttpUrl? = null
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        episodeUrl = chain.request().url
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                """{"name":"Pilot","overview":"The crew arrives.","still_path":"/pilot.jpg","runtime":48}"""
                                    .toResponseBody(),
                            ).build()
                    }.build()
            val episode = TmdbClient(http).episode("example", 6, 1, 2)
            assertEquals(TmdbEpisode("Pilot", "The crew arrives.", 48, "/pilot.jpg"), episode)
            assertEquals("/3/tv/6/season/1/episode/2", episodeUrl?.encodedPath)
            assertEquals("en-US", episodeUrl?.queryParameter("language"))
        }
}
