package com.android1500.gpssetter.update

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GitHubRelease {


    @SerializedName("id")
    @Expose
    var id: Int? = null


    @SerializedName("tag_name")
    @Expose
    var tagName: String? = null


    @SerializedName("name")
    @Expose
    var name: String? = null


    @SerializedName("published_at")
    @Expose
    var publishedAt: String? = null

    @SerializedName("assets")
    @Expose
    var assets: List<Asset>? = null

    @SerializedName("body")
    @Expose
    var body: String? = null

    class Asset {

        @SerializedName("id")
        @Expose
        var id: Int? = null

        @SerializedName("name")
        @Expose
        var name: String? = null

        @SerializedName("browser_download_url")
        @Expose
        var browserDownloadUrl: String? = null
    }


}