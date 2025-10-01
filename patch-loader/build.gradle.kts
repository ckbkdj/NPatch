import java.util.Locale
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.agp.app)
}

android {
    defaultConfig {    val copyDexTask = tasks.register<Copy>("copyDex$variantCapped") {
        dependsOn(variant.assembleTask)
        from(
            layout.buildDirectory.file("intermediates/dex/${variant.name}/mergeDex$variantCapped/classes.dex")
        )
        multiDexEnabled = false
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/jni/CMakeLists.txt")
        }
    }
    namespace = "org.lsposed.lspatch.loader"
}

// Use tasks.named() to get a provider for the assemble task
val assembleTaskProvider = androidComponents.selector().all().withBuildType("release").map {
    tasks.named("assembleRelease")
}

androidComponents.onVariants { variant ->
    val variantCapped = variant.name.replaceFirstChar { it.uppercase() }
    val projectDir = rootProject.layout.projectDirectory

    val copyDexTask = tasks.register<Copy>("copyDex$variantCapped") {
        dependsOn(variant.assembleTask)
        from(
            layout.buildDirectory.file("intermediates/dex/${variant.name}/mergeDex$variantCapped/classes.dex")
        )
        rename("classes.dex", "loader.dex")

        into(projectDir.dir("out/assets/${variant.name}/lspatch"))
    }

    val copySoTask = tasks.register<Copy>("copySo$variantCapped") {
        dependsOn(variant.assembleTask)
        dependsOn("strip${variantCapped}DebugSymbols")

        val strippedLibsDir = layout.buildDirectory.dir("intermediates/stripped_native_libs/${variant.name}/strip${variantCapped}DebugSymbols/out/lib")
        from(
            fileTree(
                "dir" to strippedLibsDir,
                "include" to listOf("**/liblspatch.so")
            )
        )
        into(projectDir.dir("out/assets/${variant.name}/lspatch/so"))
    }

    tasks.register("copy$variantCapped") {
        dependsOn(copySoTask)
        dependsOn(copyDexTask)

        doLast {
            println("Dex and so files has been copied to ${projectDir.asFile}${File.separator}out")
        }
    }
}

dependencies {
    compileOnly(projects.hiddenapi.stubs)
    implementation(projects.core)
    implementation(projects.hiddenapi.bridge)
    implementation(projects.services.daemonService)
    implementation(projects.share.android)
    implementation(projects.share.java)

    implementation(libs.gson)
}
