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
// starter-jdbc는 2026-09-16에 붙었습니다. App·QR 마스터 표 때문이 아니라
// BO 마스터를 읽는 postgres_fdw 뷰를 담을 데이터베이스가 필요해서입니다 —
// 외부 테이블은 어떤 데이터베이스 안에 있어야 합니다 (ADR-0008).
// 자기 표는 아직 없습니다.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // 손님 앱 푸시(A4): order-events 구독. Order 가 내는 쪽과 같은 라이브러리.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    runtimeOnly("org.postgresql:postgresql")

    // APP 주문 원장이 생기면서 이 서비스도 자기 표를 갖게 됐습니다.
    // schema.sql 이 아니라 Flyway 인 이유: 이 표들은 거래 데이터라
    // 앞으로 컬럼이 붙습니다. 기동마다 도는 schema.sql 로는 "무엇이
    // 언제 바뀌었나"를 알 수 없고, Order 가 이미 같은 이유로 Flyway 입니다.
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}
