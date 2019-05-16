package com.wixpress.build.bazel

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


class InMemoryBazelRepository(bazelLocalWorkspace: BazelLocalWorkspace) extends BazelRepository {

  private val branchToChangeLog = mutable.HashMap.empty[String, ListBuffer[Change]].withDefaultValue(ListBuffer.empty)

  override def localWorkspace(): BazelLocalWorkspace = bazelLocalWorkspace

  override def persist(branchName: String, changeSet: Set[String], message: String): Unit = {
    val changeLog = branchToChangeLog(branchName)
    changeLog += Change(changeSet, message)
    branchToChangeLog.put(branchName, changeLog)
  }

  def allChangesInBranch(branchName: String): List[Change] = branchToChangeLog(branchName).toList
}

case class Change(filePaths: Set[String], message: String)