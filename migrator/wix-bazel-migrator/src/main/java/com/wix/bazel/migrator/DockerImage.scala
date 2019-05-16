package com.wix.bazel.migrator

case class DockerImage(registry: String, repository: String, tag: String) {

  /*
   * Common docker images can be written as <name>:<tag>, e.g. mysql:5.7, which omits the default values for registry
   * and repository prefix.
   * The default registry is index.docker.io and the repository is prefixed with 'library/'.
   * The full name is then 'index.docker.io/library/mysql:5.7'
   * This code allows both forms to be used. It's needed because people usually use the short form for 3rd party images
   * and the long form for wix-built images.
   * As for the tag, the docker client allows one to omit it altogether, in which case it defaults to :latest, but I've
   * decided the tag should be given explicitly
  */

  def name: String = s"${encodeSlashes(repository.replace("library/", ""))}_$tag"

  def tarName: String = s"$name.tar"

  def toContainerPullRule: String = {
    s"""  container_pull(
       |    name = "$name",
       |    registry = "$registry",
       |    repository = "$repository",
       |    tag = "$tag"
       |  )""".stripMargin
  }

  def toContainerImageRule: String = s"""container_image(name="$name", base="@$name//image")"""

  private def encodeSlashes(str: String): String = str.replace('/', '_')
}

object DockerImage {


  def apply(registry: Option[String], repository: String, tag: String): DockerImage = {

    val defaultRegistry = "index.docker.io"
    registry match {
      case Some(givenRegistry) => DockerImage(givenRegistry, repository, tag)
      case None => DockerImage(defaultRegistry, fullRepo(repository), tag)
    }
  }

  def apply(imageNameFromUser: String): DockerImage = {

    val dockerImage = """((.+?\..+?)\/)?(.+):(.+)""".r

    imageNameFromUser match {
      case dockerImage(_, registry, repository, tag) => DockerImage(Option(registry),repository,tag)
      case _ => throw new RuntimeException(s"Invalid docker image: [$imageNameFromUser]. See the docs for more info: https://github.com/wix-private/bazel-tooling/blob/master/migrator/docs/docker.md")
    }
  }
  
  private def fullRepo(repository: String): String = {
    if (repository.contains('/'))
      repository
    else
      s"library/$repository"
  }
}