plugins {
	java
	jacoco
	id("org.springframework.boot") version "4.0.3"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.dailymail"
version = "0.0.1-SNAPSHOT"
description = "Daily morning mailing system - CS knowledge + News brief"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	implementation("org.commonmark:commonmark:0.24.0")
	implementation("com.google.api-client:google-api-client:2.7.2")
	implementation("com.google.apis:google-api-services-calendar:v3-rev20250115-2.0.0")
	implementation("com.google.auth:google-auth-library-oauth2-http:1.32.1")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

jacoco {
	toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
	classDirectories.setFrom(files(classDirectories.files.map {
		fileTree(it) {
			exclude(
				"com/dailymail/DailyMailApplication.class",
				"com/dailymail/today/GoogleCalendarClientFactory.class"
			)
		}
	}))
}

tasks.jacocoTestCoverageVerification {
	violationRules {
		rule {
			limit {
				minimum = "0.60".toBigDecimal()
			}
		}
	}
}
