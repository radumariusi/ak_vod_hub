package io.github.radumariusi.akvodhub.data.playlist.xtream

import io.github.radumariusi.akvodhub.parser.playlist.JsonObjectStreamException
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class XtreamJsonTest {
    @Test
    fun readsDeclaredResolutionWithoutASeparateRequest() {
        val root =
            """
            [
              {"stream_id":"21","name":"A","resolution":"1920x800"},
              {"stream_id":"22","name":"B","video":{"width":3840,"height":"2160"}},
              {"stream_id":"23","name":"C","resolution":{"unknown":1}}
            ]
            """.trimIndent()
        val movies = XtreamJson.objects(root.byteInputStream()).mapNotNull(XtreamJson::movie).toList()
        assertEquals(listOf(VideoQuality.FHD, VideoQuality.UHD, null), movies.map { it.quality })
    }

    @Test
    fun readsMoviesWithInconsistentTypes() {
        val json =
            """
            [
              {"num":1,"name":"EN| The Matrix (1999) 4K","stream_type":"movie","stream_id":"2001",
               "stream_icon":"http://img.invalid/m.jpg","added":"1700000000","category_id":"10",
               "container_extension":"mkv","tmdb":"603"},
              {"name":"|DE| Matrix 1999","stream_id":2002,"tmdb":"","category_id":null,"added":1700000001},
              {"name":"fără id"},
              {"name":"RO - Matrix","stream_id":2003.0,"tmdb_id":0}
            ]
            """.trimIndent()
        val movies = XtreamJson.objects(json.byteInputStream()).mapNotNull(XtreamJson::movie).toList()
        assertEquals(3, movies.size)
        assertEquals(
            XtreamMovie(2001, "EN| The Matrix (1999) 4K", "10", "http://img.invalid/m.jpg", "mkv", 603, 1700000000),
            movies[0],
        )
        assertEquals(2002L, movies[1].streamId)
        assertNull(movies[1].tmdbId)
        assertNull(movies[1].categoryId)
        assertEquals(2003L, movies[2].streamId)
        assertNull(movies[2].tmdbId)
    }

    @Test
    fun distinguishesEmptyListFromMissingResponse() {
        assertEquals(0, XtreamJson.objects("[]".byteInputStream()).count())
        assertThrows(JsonObjectStreamException::class.java) {
            XtreamJson.objects("".byteInputStream()).count()
        }
        assertEquals(0, XtreamJson.objects("{}".byteInputStream()).mapNotNull(XtreamJson::movie).count())
    }

    @Test
    fun readsSeriesAndEpisodes() {
        val series =
            XtreamJson
                .objects(
                    """[{"series_id":"77","name":"Breaking Bad","cover":"c.jpg","tmdb":"1396"}]""".byteInputStream(),
                ).mapNotNull(XtreamJson::series)
                .single()
        assertEquals(77L, series.seriesId)
        assertEquals(1396L, series.tmdbId)

        val info =
            XtreamJson.parse(
                """
                {"seasons":[],"info":{"name":"Breaking Bad"},
                 "episodes":{"1":[{"id":"9001","episode_num":1,"title":"Pilot","container_extension":"mkv","season":1},
                                  {"id":"9002","episode_num":"2","title":"Cat's in the Bag"}],
                             "2":[{"id":"9101","episode_num":1,"title":"Seven Thirty-Seven"}]}}
                """.trimIndent(),
            )
        val episodes = XtreamJson.episodes(info)
        assertEquals(3, episodes.size)
        assertEquals(XtreamEpisode(9002, "Cat's in the Bag", 1, 2, null, null), episodes[1])
        assertEquals(2, episodes[2].season)

        val listForm = XtreamJson.parse("""{"episodes":[[{"id":1,"season":3,"episode_num":4,"title":"x"}]]}""")
        assertEquals(3, XtreamJson.episodes(listForm).single().season)
    }

    @Test
    fun invalidEpisodeResponseCannotConfirmAnEmptySeason() {
        val empty = XtreamJson.parse("""{"episodes":{}}""")
        val flat = XtreamJson.parse("""{"episodes":[{"id":"41","season":"2","episode_num":3}]}""")
        assertEquals(emptyList<XtreamEpisode>(), XtreamJson.episodes(empty))
        assertEquals(2, XtreamJson.episodes(flat).single().season)
        assertThrows(XtreamException::class.java) { XtreamJson.episodes(XtreamJson.parse("""{"error":"blocked"}""")) }
        assertThrows(
            XtreamException::class.java,
        ) { XtreamJson.episodes(XtreamJson.parse("""{"episodes":{"1":[{}]}}""")) }
    }

    @Test
    fun readsCategories() {
        val categories =
            XtreamJson.categories(
                XtreamJson.parse("""[{"category_id":"10","category_name":"EN | Movies 4K"},{"category_name":"x"}]"""),
            )
        assertEquals(mapOf("10" to "EN | Movies 4K"), categories)
    }

    @Test
    fun normalizesServerAddress() {
        assertEquals("http://host.invalid:8080", XtreamAccount.normalizeServer(" host.invalid:8080/ "))
        assertEquals("https://host.invalid", XtreamAccount.normalizeServer("https://host.invalid/"))
        assertEquals(
            "http://host.invalid:80",
            XtreamAccount.normalizeServer("http://host.invalid:80/get.php?username=a&password=b&type=m3u_plus"),
        )
    }

    @Test
    fun buildsStreamUrls() {
        val account = XtreamAccount("http://host.invalid:8080", "u", "p")
        assertEquals("http://host.invalid:8080/movie/u/p/5.mkv", XtreamClient.streamUrl(account, "movie", 5, ".mkv"))
        assertEquals("http://host.invalid:8080/series/u/p/6.mp4", XtreamClient.streamUrl(account, "series", 6, null))
    }
}
