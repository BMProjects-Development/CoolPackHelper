package org.bmp.cph.util

import java.net.http.HttpClient
import java.time.Duration

/** Shared connection pools with separate policies for API traffic and large transfers. */
object CphHttpClients {
    val api: HttpClient = client(Duration.ofSeconds(12))
    val transfer: HttpClient = client(Duration.ofSeconds(15))
    val image: HttpClient = client(Duration.ofSeconds(10))

    private fun client(connectTimeout: Duration): HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
}
