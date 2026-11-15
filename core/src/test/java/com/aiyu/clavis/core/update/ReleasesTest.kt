package com.aiyu.clavis.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleasesTest {
    private fun v(s: String) = Version.parse(s)!!
    private fun rel(tag: String, vararg assets: String) =
        Release(v(tag), tag, "", assets.map { ReleaseAsset(it, "https://x/$it", 1) })

    @Test fun ordering() {
        assertNotNull(Version.parse("v1.2.3-beta.1"))
        assertNull(Version.parse("nightly"))
        assert(v("1.0.0") > v("1.0.0-beta.9"))
        assert(v("1.0.0-beta.1") > v("1.0.0-alpha.9"))
        assert(v("1.0.1-alpha.1") > v("1.0.0"))
    }

    @Test fun stableInstallIgnoresPrereleases() {
        val all = listOf(rel("1.0.1-beta.1"), rel("1.0.1"), rel("1.1.0-alpha.1"))
        assertEquals(v("1.0.1"), Releases.pick(all, v("1.0.0"))?.version) // alpha/beta of newer line are not offered
    }

    @Test fun betaInstallSeesStable() {
        assertEquals(v("1.0.1"), Releases.pick(listOf(rel("1.0.1"), rel("1.0.1-beta.2")), v("1.0.1-beta.1"))?.version)
    }

    @Test fun nothingNewer() = assertNull(Releases.pick(listOf(rel("1.0.0")), v("1.0.0")))

    @Test fun apkMatching() {
        assertEquals("Clavis-1.0.0.apk", Releases.apkFor(rel("1.0.0", "notes.txt", "Clavis-1.0.0.apk"))?.name)
        assertNull(Releases.apkFor(rel("1.0.0", "Clavis-1.0.0.aab")))
    }

    @Test fun parseSkipsDraftsAndUnknownTags() {
        val body = """[{"tag_name":"v1.0.0","name":"v1.0.0","assets":[{"name":"Clavis-1.0.0.apk","browser_download_url":"u","size":5,"digest":"sha256:AB"}]},
            {"tag_name":"v2.0.0","draft":true},{"tag_name":"weird"}]"""
        val r = Releases.parse(body).single()
        assertEquals("1.0.0", r.name)
        assertEquals("ab", r.assets.single().sha256)
    }
}
