val rdf4j = "3.1.0"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("org.eclipse.rdf4j:rdf4j-client:$rdf4j")
    implementation("dk.brics:automaton:1.12-1")
    implementation("org.la4j:la4j:0.6.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.3.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.3.1")
    testRuntimeOnly("org.slf4j:slf4j-simple:1.7.25")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}