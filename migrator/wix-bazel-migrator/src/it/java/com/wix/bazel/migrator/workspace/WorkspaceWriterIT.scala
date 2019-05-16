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

      File(repoRoot.resolve("WORKSPACE")).contentAsString must not(contain("grpc_repository_for_isolated_mode"))
    }

    "not load grpc_repositories from bazel_proto_poc in case inter repo dependency flag is on and include-server-infra flag is on" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, workspaceName, interRepoSourceDependency = true, includeServerInfraInSocialModeSet = true)
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must not(contain("grpc_repository_for_isolated_mode"))
    }

    "load grpc_repositories from bazel_proto_poc in case inter repo dependency flag is on and include-server-infra flag is off" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, workspaceName, interRepoSourceDependency = true, includeServerInfraInSocialModeSet = true)
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must not(contain("grpc_repository_for_isolated_mode"))
    }

    "load grpc_repositories from poc when migrating non server-infra repo" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, workspaceName, interRepoSourceDependency = true, includeServerInfraInSocialModeSet = false)
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must contain("grpc_repository_for_isolated_mode")
    }

    "load jar_jar repositories from github" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, workspaceName)
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must contain("git@github.com:johnynek/bazel_jar_jar.git")
    }

    "load third parties of external repos when cross-repo flag is true" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, "workspace_name", interRepoSourceDependency = true)
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must contain(s"""third_party_deps_of_external_wix_repositories""")
    }

    "not load fw snapshots for any repo" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, "workspace_name")
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must not contain(s"""fw_snapshot_dependencies""")
    }

    "register default local host execution platform" in new ctx {
      val writer = new WorkspaceWriter(repoRoot, "workspace_name")
      writer.write()

      File(repoRoot.resolve("WORKSPACE")).contentAsString must contain("""register_execution_platforms("@core_server_build_tools//platforms:my_host_platform")""")
    }
  }

  abstract class ctx extends baseCtx {
    val workspaceName = "workspace_name"
  }

  abstract class serverInfraCtx extends baseCtx {
    val serverInfraWorkspaceName = "server_infra"
  }
}
