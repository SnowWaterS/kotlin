description = "Kotlin/Native Library Commonizer (for using with embeddable compiler)"

plugins {
    java
}

dependencies {
    embedded(project(":native:kotlin-native-commonizer")) { isTransitive = false }
    runtime(kotlinStdlib())
    runtime(project(":kotlin-compiler-embeddable"))
}

sourceSets {
    "main" {}
    "test" {}
}

publish()

noDefaultJar()

runtimeJar(rewriteDefaultJarDepsToShadedCompiler())
sourcesJar()
javadocJar()
