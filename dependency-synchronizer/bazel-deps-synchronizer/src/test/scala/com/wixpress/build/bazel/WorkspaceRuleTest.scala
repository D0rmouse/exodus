package com.wixpress.build.bazel

import com.wix.build.maven.translation.MavenToBazelTranslations._
import com.wixpress.build.maven.{Coordinates, Packaging}
import org.specs2.mutable.SpecificationWithJUnit

class WorkspaceRuleTest extends SpecificationWithJUnit {


  "MavenJarRule" should {

    "return valid maven_jar bazel rule to given maven coordinates" in  {
      val someCoordinates = Coordinates(
        groupId = "some.group",
        artifactId = "some-artifact",
        version = "5.0"
      )
      val expectedMavenJarRuleText =
        s"""  if native.existing_rule("${someCoordinates.workspaceRuleName}") == None:
           |    native.maven_jar(
           |        name = "${someCoordinates.workspaceRuleName}",
           |        artifact = "${someCoordinates.serialized}"
           |    )""".stripMargin

      WorkspaceRule.of(someCoordinates).serialized mustEqual expectedMavenJarRuleText
    }

    "return valid maven_proto to given proto coordinates" in  {
      val someArchiveCoordinates = Coordinates(
        groupId = "some.group.id",
        artifactId = "artifact-id",
        version = "version",
        packaging = Packaging("zip"),
        classifier = Some("proto")
      )
      val expectedWorkspaceRuleText =
        s"""  if native.existing_rule("${someArchiveCoordinates.workspaceRuleName}") == None:
           |    maven_proto(
           |        name = "${someArchiveCoordinates.workspaceRuleName}",
           |        artifact = "${someArchiveCoordinates.serialized}"
           |    )""".stripMargin

      WorkspaceRule.of(someArchiveCoordinates).serialized mustEqual expectedWorkspaceRuleText
    }

    "return valid maven_archive to maven zip artifact that is not proto" in  {
      val someArchiveCoordinates = Coordinates(
        groupId = "some.group.id",
        artifactId = "artifact-id",
        version = "version",
        packaging = Packaging("zip")
      )
      val expectedWorkspaceRuleText =
        s"""  if native.existing_rule("${someArchiveCoordinates.workspaceRuleName}") == None:
           |    maven_archive(
           |        name = "${someArchiveCoordinates.workspaceRuleName}",
           |        artifact = "${someArchiveCoordinates.serialized}"
           |    )""".stripMargin

      WorkspaceRule.of(someArchiveCoordinates).serialized mustEqual expectedWorkspaceRuleText
    }

    "return valid maven_archive to maven tar.gz artifact" in  {
      val someArchiveCoordinates = Coordinates(
        groupId = "some.group.id",
        artifactId = "artifact-id",
        version = "version",
        packaging = Packaging("tar.gz")
      )
      val expectedWorkspaceRuleText =
        s"""  if native.existing_rule("${someArchiveCoordinates.workspaceRuleName}") == None:
           |    maven_archive(
           |        name = "${someArchiveCoordinates.workspaceRuleName}",
           |        artifact = "${someArchiveCoordinates.serialized}"
           |    )""".stripMargin

      WorkspaceRule.of(someArchiveCoordinates).serialized mustEqual expectedWorkspaceRuleText
    }

    "throw exception in case undefined packaging" in  {
      val someArchiveCoordinates = Coordinates(
        groupId = "some.group.id",
        artifactId = "artifact-id",
        version = "version",
        packaging = Packaging("strange-packaging")
      )

      WorkspaceRule.of(someArchiveCoordinates).serialized must throwA[RuntimeException]
    }

  }
}
