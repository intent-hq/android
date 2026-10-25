package dev.intent.tailcat

/** No native library is loaded in builds without the AAR. */
object Tailcat : TunnelClientFactory by UnavailableTunnelClientFactory
