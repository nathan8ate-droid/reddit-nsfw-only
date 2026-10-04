group = "io.github.redditnsfwonly"

patches {
    about {
        name = "Reddit NSFW Only"
        description = "Keeps mature Reddit content and filters confirmed non-NSFW feed posts"
        source = "git@github.com:nathan8ate-droid/reddit-nsfw-only.git"
        author = "nathan8ate-droid"
        contact = "https://github.com/nathan8ate-droid/reddit-nsfw-only/issues"
        website = "https://github.com/nathan8ate-droid/reddit-nsfw-only"
        license = "GNU General Public License v3.0, with preserved upstream NOTICE requirements"
    }
}

dependencies {
    implementation(libs.guava)
    implementation(libs.morphe.patches.library)
}
