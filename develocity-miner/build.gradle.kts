plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ksp)
}

kotlin.compilerOptions.optIn.addAll(
  "kotlinx.coroutines.ExperimentalCoroutinesApi",
  "kotlinx.coroutines.FlowPreview",
)

dependencies {
  ksp(libs.autoService.ksp)
  implementation(libs.autoService.annotations)
  implementation(libs.clikt)
  implementation(libs.develocityApi)
  implementation(libs.picnic)
  implementation(projects.sgpCommon)
}
