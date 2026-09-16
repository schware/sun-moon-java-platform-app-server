plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.sunmoon.appserver"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

// APP-SERVER — sun-moon-java-platform family. 손님 쪽 창구이고, 단말에게
// POS-SERVER(Device Server)가 하는 역할을 손님 앱에게 합니다 — umbrella
// docs/adr/0007. delivery를 본으로 만들었습니다(order는 예외라 복사하면
// 안 됩니다 — docs/CONVENTIONS §4).
//
// persistence starter가 아직 없습니다. App·QR 마스터의 표가 정해지면 그때
// starter-jdbc와 postgresql을 붙입니다. 지금 선언해 두면 datasource URL이
// 없어 기동 자체가 실패합니다.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
