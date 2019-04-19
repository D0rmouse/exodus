load("//:import_external.bzl", import_external = "safe_wix_scala_maven_import_external")

def dependencies():

  import_external(
      name = "com_toedter_jcalendar",
      artifact = "com.toedter:jcalendar:1.3.2",
      jar_sha256 = "2aa64c67eee507d3f4b92b95d61ced3e5a96dba6de49c4baa6377735808866db",
      srcjar_sha256 = "8ea896989277c82b0ec8f88aa54213d568fe80966da69227223ea5656b1d4316",
  )
