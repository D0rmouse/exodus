package com.wix.bazel.migrator.workspace

import com.wix.bazel.migrator.BaseWriterIT

class WorkspaceWriterIT extends BaseWriterIT {
import better.files.File
  "BazelCustomRunnerWriter" should {
    "write workspace resolving script and a custom script that calls the former script and then runs bazel" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, workspaceName)
      writer.write()

      repoRoot.resolve("WORKSPACE") must beRegularFile
    }

    "write workspace name according to given name" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, "workspace_name")
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must contain(s"""workspace(name = "workspace_name")""")
    }

    "load grpc_repositories from server-infra when migrating server-infra" in new serverInfraCtx {
      val writer = new WorkspaceWriter(repoRoot, serverInfraWorkspaceName)
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must contain(
        """load("//framework/grpc/generator-bazel/src/main/rules:wix_scala_proto_repositories.bzl","grpc_repositories")""")
    }

    "load grpc_repositories from poc when migrating non server-infra repo" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, workspaceName)
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must contain(
        """load("@wix_grpc//src/main/rules:wix_scala_proto_repositories.bzl","grpc_repositories")""")
    }
  }

  abstract class ctx extends baseCtx {
    val workspaceName = "workspace_name"
  }

  abstract class serverInfraCtx extends baseCtx {
    val serverInfraWorkspaceName = "server_infra"
  }
}
