// Publish the local archive as a project dependency so AGP packages its JNI libraries.
configurations.maybeCreate("default")
artifacts.add("default", file("../libs/tailcatkit.aar"))
