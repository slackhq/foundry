plugins {
  alias(libs.plugins.example)
}

slack {
  features {
    compose()
  }
  android {
    features {
      robolectric()
    }
  }
}
