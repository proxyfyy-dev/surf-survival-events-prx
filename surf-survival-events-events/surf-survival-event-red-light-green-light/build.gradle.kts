plugins {
    id("dev.slne.surf.api.gradle.paper-plugin")
}

surfPaperPluginApi {
    mainClass("dev.slne.surf.survival.events.red.light.green.light.PaperMain")
    generateLibraryLoader(false)
    foliaSupported(true)
}