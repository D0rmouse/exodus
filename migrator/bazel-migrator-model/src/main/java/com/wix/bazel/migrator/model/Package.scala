package com.wix.bazel.migrator.model

//TODO relativePathFromMonoRepoRoot String or Path
case class Package(relativePathFromMonoRepoRoot: String, var targets: Set[Target], originatingSourceModule: SourceModule)
