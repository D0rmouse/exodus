package com.wix.bazel.migrator.transform

import com.wix.bazel.migrator.model.TestType

private[transform] case class Code(codePath: CodePath, dependencies: List[Dependency] = Nil, externalSourceDependencies: Set[String] = Set.empty) {
  def testType: TestType = TestType.from(codePath.filePath)
}
