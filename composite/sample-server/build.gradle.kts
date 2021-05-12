plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("application")
}

dependencies {
  api("com.expediagroup:graphql-kotlin-spring-server:4.1.0")
  implementation(groovy.util.Eval.x(project, "x.dep.kotlin.coroutinesReactive"))
}

application {
  mainClass.set("com.apollographql.apollo.sample.server.MainKt")
}